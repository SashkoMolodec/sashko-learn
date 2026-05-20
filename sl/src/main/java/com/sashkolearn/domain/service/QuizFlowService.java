package com.sashkolearn.domain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sashkolearn.api.telegram.TelegramChatBot;
import com.sashkolearn.infrastructure.redis.RedisService;
import com.sashkolearn.domain.model.QuizQuestionView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class QuizFlowService {

    private static final String QUIZ_QUESTIONS_PREFIX = "quiz:questions:";
    private static final String POLL_MAPPING_PREFIX = "quiz:poll:";
    private static final int QUIZ_DATA_TTL = 7200; // 2 hours

    private final RedisService redisService;
    private final SessionManagementService sessionService;
    private final TelegramChatBot telegramBot;

    public QuizFlowService(
            RedisService redisService,
            SessionManagementService sessionService,
            @Lazy TelegramChatBot telegramBot
    ) {
        this.redisService = redisService;
        this.sessionService = sessionService;
        this.telegramBot = telegramBot;
    }

    /**
     * Store quiz questions in Redis for later use during quiz taking
     */
    public void storeQuizQuestions(UUID quizId, List<QuizQuestionView> questions) {
        String key = QUIZ_QUESTIONS_PREFIX + quizId;
        redisService.setObject(key, questions, QUIZ_DATA_TTL);
        log.info("Stored {} questions for quiz {}", questions.size(), quizId);
    }

    /**
     * Get quiz questions from Redis
     */
    public List<QuizQuestionView> getQuizQuestions(UUID quizId) {
        String key = QUIZ_QUESTIONS_PREFIX + quizId;
        return redisService.getObject(key, new TypeReference<>() {});
    }

    /**
     * Start a quiz session for the user
     */
    public void startQuiz(Long chatId, UUID quizId, String topic, List<QuizQuestionView> questions) {
        // Shuffle questions for variety on each attempt
        List<QuizQuestionView> shuffled = new ArrayList<>(questions);
        Collections.shuffle(shuffled);

        // Store questions in Redis
        storeQuizQuestions(quizId, shuffled);

        // Initialize session
        sessionService.saveQuizSession(chatId, quizId.toString(), shuffled.size(), 1, 0);

        telegramBot.sendMessage(chatId, String.format("🎯 *Квіз розпочато!*\n\nТема: _%s_\nПитань: *%d*",
                topic, shuffled.size()));

        // Send first question as native poll
        sendQuestionPoll(chatId, quizId, shuffled.get(0), 1, shuffled.size());
    }

    /**
     * Handle poll answer from Telegram
     */
    public void handlePollAnswer(Long userId, String pollId, List<Integer> selectedOptions) {
        // Get poll mapping from Redis
        PollMapping mapping = getPollMapping(pollId);
        if (mapping == null) {
            log.warn("No mapping found for poll {}", pollId);
            return;
        }

        SessionManagementService.QuizSession session = sessionService.getQuizSession(userId);
        if (session == null) {
            log.warn("No quiz session found for user {}", userId);
            return;
        }

        UUID quizId = UUID.fromString(mapping.quizId());
        int questionNumber = mapping.questionNumber();
        int correctOptionIndex = mapping.correctOptionIndex();

        // Check if answer is correct
        boolean isCorrect = !selectedOptions.isEmpty() && selectedOptions.get(0) == correctOptionIndex;
        int newScore = session.score() + (isCorrect ? 1 : 0);

        log.info("User {} answered question {} of quiz {}: correct={}, newScore={}",
                userId, questionNumber, quizId, isCorrect, newScore);

        // Send detailed explanation
        if (mapping.explanation() != null && !mapping.explanation().isBlank()) {
            String explanationMessage = String.format("💡 *пояснення:*\n\n%s", mapping.explanation());
            telegramBot.sendMessage(userId, explanationMessage);
        }

        // Check if quiz is complete
        if (questionNumber >= session.totalQuestions()) {
            // Quiz completed
            showFinalResults(userId, newScore, session.totalQuestions());
            sessionService.clearQuizSession(userId);
        } else {
            // Next question
            int nextQuestionNumber = questionNumber + 1;
            sessionService.saveQuizSession(userId, quizId.toString(), session.totalQuestions(), nextQuestionNumber, newScore);

            List<QuizQuestionView> questions = getQuizQuestions(quizId);
            if (questions != null) {
                QuizQuestionView nextQuestion = nextQuestionNumber <= questions.size()
                        ? questions.get(nextQuestionNumber - 1)
                        : null;

                if (nextQuestion != null) {
                    // Small delay before next question
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    sendQuestionPoll(userId, quizId, nextQuestion, nextQuestionNumber, session.totalQuestions());
                }
            }
        }
    }

    /**
     * Send a quiz question as native Telegram poll
     */
    private void sendQuestionPoll(Long chatId, UUID quizId, QuizQuestionView question, int currentNum, int totalNum) {
        String questionText = String.format("питання %d/%d:\n%s",
                currentNum, totalNum, question.question());

        // Telegram poll question max length is 300
        if (questionText.length() > 300) {
            questionText = questionText.substring(0, 297) + "...";
        }

        String correctText = switch (question.correctOption().toUpperCase()) {
            case "A" -> truncateOption(question.optionA());
            case "B" -> truncateOption(question.optionB());
            case "C" -> truncateOption(question.optionC());
            case "D" -> truncateOption(question.optionD());
            default  -> truncateOption(question.optionA());
        };

        List<String> options = new ArrayList<>(List.of(
                truncateOption(question.optionA()),
                truncateOption(question.optionB()),
                truncateOption(question.optionC()),
                truncateOption(question.optionD())
        ));
        Collections.shuffle(options);
        int correctIndex = options.indexOf(correctText);

        // Don't pass explanation to poll - we'll send it as a separate message after answering
        String pollId = telegramBot.sendQuizPoll(
                chatId,
                questionText,
                options,
                correctIndex,
                null
        );

        if (pollId != null) {
            // Store sequential currentNum (not DB questionNumber) for correct next-question lookup
            savePollMapping(pollId, new PollMapping(quizId.toString(), currentNum, correctIndex, question.explanation()));
        }
    }

    private String truncateOption(String option) {
        // Telegram poll option max length is 100
        if (option.length() > 100) {
            return option.substring(0, 97) + "...";
        }
        return option;
    }

    private void savePollMapping(String pollId, PollMapping mapping) {
        String key = POLL_MAPPING_PREFIX + pollId;
        redisService.setObject(key, mapping, QUIZ_DATA_TTL);
    }

    private PollMapping getPollMapping(String pollId) {
        String key = POLL_MAPPING_PREFIX + pollId;
        return redisService.getObject(key, new TypeReference<>() {});
    }

    private void showFinalResults(Long chatId, int score, int total) {
        int percentage = (int) Math.round((double) score / total * 100);

        String emoji;
        if (percentage >= 90) emoji = "🏆";
        else if (percentage >= 70) emoji = "🎉";
        else if (percentage >= 50) emoji = "👍";
        else emoji = "📚";

        String message = String.format("""
                %s *квіз завершено!*

                результат: *%d/%d* (%d%%)

                %s
                """,
                emoji, score, total, percentage,
                percentage >= 70 ? "люкс!" : "ше раз, слобонько!");

        telegramBot.sendMessage(chatId, message);
    }

    // Record for poll mapping (includes explanation for sending after answer)
    public record PollMapping(String quizId, int questionNumber, int correctOptionIndex, String explanation) {}
}
