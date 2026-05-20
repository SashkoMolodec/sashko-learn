package com.sashkolearn.mainagent.messaging.consumer;

import com.sashkolearn.mainagent.api.telegram.TelegramChatBot;
import com.sashkolearn.mainagent.domain.service.QuizFlowService;
import com.sashkolearn.mainagent.messaging.consumer.dto.GetQuizResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetQuizResultListener {

    private final TelegramChatBot telegramBot;
    private final QuizFlowService quizFlowService;

    @KafkaListener(topics = "quiz-get-results", groupId = "main-agent-group")
    public void handleGetQuizResult(GetQuizResultDto result) {
        log.info("Received quiz-get result for chat: {}", result.chatId());

        if (!result.success()) {
            String errorMessage = "❌ Помилка завантаження квізу: " + result.errorMessage();
            telegramBot.sendMessage(result.chatId(), errorMessage);
            return;
        }

        try {
            if (result.questions() == null || result.questions().isEmpty()) {
                telegramBot.sendMessage(result.chatId(), "❌ Квіз не містить питань");
                return;
            }

            // Start the quiz
            quizFlowService.startQuiz(
                    result.chatId(),
                    result.quizId(),
                    result.topic(),
                    result.questions()
            );

        } catch (Exception e) {
            log.error("Failed to process quiz-get result for chat {}", result.chatId(), e);
            telegramBot.sendMessage(result.chatId(), "❌ Помилка запуску квізу");
        }
    }
}
