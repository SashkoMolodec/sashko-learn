package com.sashkolearn.domain.service;

import com.sashkolearn.infrastructure.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionManagementService {

    private final RedisService redisService;

    private static final String SESSION_PREFIX = "session:";
    private static final String QUIZ_SESSION_PREFIX = "quiz_session:";
    private static final int SESSION_TTL = 3600;       // 1 hour (book upload flow)
    private static final int QUIZ_SESSION_TTL = 172800; // 48 hours — matches QuizFlowService.QUIZ_DATA_TTL so poll mappings and session expire together

    public void saveCurrentBook(Long chatId, String bookId) {
        String key = SESSION_PREFIX + chatId + ":current_book";
        redisService.set(key, bookId, SESSION_TTL);
        log.info("Saved current book {} for chat {}", bookId, chatId);
    }

    public String getCurrentBook(Long chatId) {
        String key = SESSION_PREFIX + chatId + ":current_book";
        return redisService.get(key);
    }

    public void saveQuizSession(Long chatId, String quizId, int totalQuestions, int currentQuestion, int score) {
        String key = QUIZ_SESSION_PREFIX + chatId;
        Map<String, String> session = Map.of(
            "quizId", quizId,
            "totalQuestions", String.valueOf(totalQuestions),
            "currentQuestion", String.valueOf(currentQuestion),
            "score", String.valueOf(score)
        );
        redisService.setHash(key, session, QUIZ_SESSION_TTL);
        log.info("Saved quiz session for chat {}: quiz {}, question {}/{}, score {}",
                 chatId, quizId, currentQuestion, totalQuestions, score);
    }

    public QuizSession getQuizSession(Long chatId) {
        String key = QUIZ_SESSION_PREFIX + chatId;
        Map<Object, Object> hash = redisService.getHash(key);
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        return new QuizSession(
            (String) hash.get("quizId"),
            Integer.parseInt((String) hash.get("totalQuestions")),
            Integer.parseInt((String) hash.get("currentQuestion")),
            Integer.parseInt((String) hash.get("score"))
        );
    }

    public void clearQuizSession(Long chatId) {
        String key = QUIZ_SESSION_PREFIX + chatId;
        redisService.delete(key);
        log.info("Cleared quiz session for chat {}", chatId);
    }

    public record QuizSession(
        String quizId,
        int totalQuestions,
        int currentQuestion,
        int score
    ) {}

    // Pending quiz topic (when user runs /quiz topic and we're searching)
    private static final String PENDING_QUIZ_TOPIC_PREFIX = "pending_quiz_topic:";

    public void savePendingQuizTopic(Long chatId, String topic) {
        String key = PENDING_QUIZ_TOPIC_PREFIX + chatId;
        redisService.set(key, topic, 300); // 5 minutes
    }

    public String getPendingQuizTopic(Long chatId) {
        String key = PENDING_QUIZ_TOPIC_PREFIX + chatId;
        return redisService.get(key);
    }

    public void clearPendingQuizTopic(Long chatId) {
        String key = PENDING_QUIZ_TOPIC_PREFIX + chatId;
        redisService.delete(key);
    }

    // Pending quiz key note (optional -key flag)
    private static final String PENDING_QUIZ_KEYNOTE_PREFIX = "pending_quiz_keynote:";

    public void savePendingQuizKeyNote(Long chatId, String keyNoteName) {
        String key = PENDING_QUIZ_KEYNOTE_PREFIX + chatId;
        if (keyNoteName != null && !keyNoteName.isBlank()) {
            redisService.set(key, keyNoteName, 300); // 5 minutes
        } else {
            redisService.delete(key);
        }
    }

    public String getPendingQuizKeyNote(Long chatId) {
        String key = PENDING_QUIZ_KEYNOTE_PREFIX + chatId;
        return redisService.get(key);
    }

    // Pending quiz guide (optional -guide flag)
    private static final String PENDING_QUIZ_GUIDE_PREFIX = "pending_quiz_guide:";

    public void savePendingQuizGuide(Long chatId, String guide) {
        String key = PENDING_QUIZ_GUIDE_PREFIX + chatId;
        if (guide != null && !guide.isBlank()) {
            redisService.set(key, guide, 300); // 5 minutes
        } else {
            redisService.delete(key);
        }
    }

    public String getPendingQuizGuide(Long chatId) {
        String key = PENDING_QUIZ_GUIDE_PREFIX + chatId;
        return redisService.get(key);
    }
}
