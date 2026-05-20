package com.sashkolearn.mainagent.messaging.consumer;

import com.sashkolearn.mainagent.api.telegram.TelegramChatBot;
import com.sashkolearn.mainagent.messaging.consumer.dto.AskQuestionResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AskQuestionResultListener {

    private static final int SAFE_MESSAGE_LIMIT = 4000;

    private final TelegramChatBot telegramBot;

    @KafkaListener(topics = "ask-question-results", groupId = "main-agent-group")
    public void handleAskQuestionResult(AskQuestionResultDto result) {
        log.info("Received ask-question result for chat: {}", result.chatId());

        if (!result.success()) {
            String errorMessage = "❌ Failed to answer question: " + result.errorMessage();
            telegramBot.sendMessage(result.chatId(), errorMessage);
            return;
        }

        try {
            List<String> messageParts = splitMessage(result.answer());
            for (String part : messageParts) {
                telegramBot.sendMessage(result.chatId(), part);
            }

            if (result.relevantAttachmentPaths() != null) {
                for (String filePath : result.relevantAttachmentPaths()) {
                    telegramBot.sendPhoto(result.chatId(), filePath, null);
                }
            }

        } catch (Exception e) {
            log.error("Failed to process ask-question result for chat {}", result.chatId(), e);
            telegramBot.sendMessage(result.chatId(), "❌ Error processing answer");
        }
    }

    private List<String> splitMessage(String text) {
        if (text.length() <= SAFE_MESSAGE_LIMIT) {
            return List.of(text);
        }

        List<String> parts = new ArrayList<>();
        String remaining = text;

        while (remaining.length() > SAFE_MESSAGE_LIMIT) {
            String chunk = remaining.substring(0, SAFE_MESSAGE_LIMIT);
            int splitIndex = chunk.lastIndexOf("\n\n");

            if (splitIndex <= 0) {
                splitIndex = chunk.lastIndexOf("\n");
            }

            if (splitIndex <= 0) {
                splitIndex = SAFE_MESSAGE_LIMIT;
            }

            parts.add(remaining.substring(0, splitIndex).trim());
            remaining = remaining.substring(splitIndex).trim();
        }

        if (!remaining.isEmpty()) {
            parts.add(remaining);
        }

        return parts;
    }
}
