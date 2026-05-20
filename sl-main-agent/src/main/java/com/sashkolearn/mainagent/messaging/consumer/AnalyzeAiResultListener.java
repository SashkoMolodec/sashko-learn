package com.sashkolearn.mainagent.messaging.consumer;

import com.sashkolearn.mainagent.api.telegram.TelegramChatBot;
import com.sashkolearn.mainagent.messaging.consumer.dto.AnalyzeAiResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzeAiResultListener {

    private final TelegramChatBot telegramBot;

    @KafkaListener(topics = "ai-analyze-results", groupId = "main-agent-group")
    public void handleAnalyzeAiResult(AnalyzeAiResultDto result) {
        log.info("Received ai-analyze result for chat: {}", result.chatId());

        if (!result.success()) {
            String errorMessage = "❌ Failed to analyze: " + result.errorMessage();
            telegramBot.sendMessage(result.chatId(), errorMessage);
            return;
        }

        try {
            telegramBot.sendMessage(result.chatId(), result.analysis());

        } catch (Exception e) {
            log.error("Failed to process ai-analyze result for chat {}", result.chatId(), e);
            telegramBot.sendMessage(result.chatId(), "❌ Error processing AI analysis results");
        }
    }
}
