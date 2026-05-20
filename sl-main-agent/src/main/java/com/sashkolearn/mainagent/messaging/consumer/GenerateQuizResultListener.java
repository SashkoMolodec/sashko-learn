package com.sashkolearn.mainagent.messaging.consumer;

import com.sashkolearn.mainagent.api.telegram.TelegramChatBot;
import com.sashkolearn.mainagent.messaging.consumer.dto.GenerateQuizResultDto;
import com.sashkolearn.mainagent.messaging.producer.GetQuizTaskProducer;
import com.sashkolearn.mainagent.messaging.producer.dto.GetQuizTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenerateQuizResultListener {

    private final TelegramChatBot telegramBot;
    private final GetQuizTaskProducer getQuizTaskProducer;

    @KafkaListener(topics = "quiz-generate-results", groupId = "main-agent-group")
    public void handleGenerateQuizResult(GenerateQuizResultDto result) {
        log.info("Received quiz-generate result for chat: {}", result.chatId());

        if (!result.success()) {
            String errorMessage = "❌ Помилка генерації квізу: " + result.errorMessage();
            telegramBot.sendMessage(result.chatId(), errorMessage);
            return;
        }

        try {
            telegramBot.sendMessage(result.chatId(),
                    String.format("✅ *Квіз створено!*\n\n_%s_\n\nКількість питань: *%d*\n\nЗавантажую питання...",
                            result.description(), result.questionCount()));

            // Now fetch the full quiz with questions to start it
            GetQuizTaskDto getQuizTask = new GetQuizTaskDto(result.chatId(), result.quizId());
            getQuizTaskProducer.send(getQuizTask);

        } catch (Exception e) {
            log.error("Failed to process quiz-generate result for chat {}", result.chatId(), e);
            telegramBot.sendMessage(result.chatId(), "❌ Помилка обробки згенерованого квізу");
        }
    }
}
