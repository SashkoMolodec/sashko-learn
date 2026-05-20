package com.sashkolearn.mainagent.messaging.consumer;

import com.sashkolearn.mainagent.api.telegram.TelegramChatBot;
import com.sashkolearn.mainagent.messaging.consumer.dto.FindNotesResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FindNotesResultListener {

    private final TelegramChatBot telegramBot;

    @KafkaListener(topics = "find-notes-results", groupId = "main-agent-group")
    public void handleFindNotesResult(FindNotesResultDto result) {
        log.info("Received find-notes result for chat: {}", result.chatId());

        if (!result.success()) {
            String errorMessage = "❌ Failed to find notes: " + result.errorMessage();
            telegramBot.sendMessage(result.chatId(), errorMessage);
            return;
        }

        try {
            String message = formatResult(result);
            telegramBot.sendMessage(result.chatId(), message);

        } catch (Exception e) {
            log.error("Failed to process find-notes result for chat {}", result.chatId(), e);
            telegramBot.sendMessage(result.chatId(), "❌ Error processing search results");
        }
    }

    private String formatResult(FindNotesResultDto result) {
        StringBuilder sb = new StringBuilder();
        sb.append("*😎 шос найшов*\n");

        if (result.noteNames() == null || result.noteNames().isEmpty()) {
            sb.append("Нічого не знайдено. Спробуй /sync спочатку.");
            return sb.toString();
        }

        for (int i = 0; i < result.noteNames().size(); i++) {
            sb.append(i + 1).append(". ").append(result.noteNames().get(i)).append("\n");
        }

        return sb.toString();
    }
}
