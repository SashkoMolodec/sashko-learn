package com.sashkolearn.mainagent.messaging.consumer;

import com.sashkolearn.mainagent.api.telegram.TelegramChatBot;
import com.sashkolearn.mainagent.messaging.consumer.dto.AnalyzeNoteResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzeNoteResultListener {

    private final TelegramChatBot telegramBot;

    @KafkaListener(topics = "analyze-note-results", groupId = "main-agent-group")
    public void handleAnalyzeNoteResult(AnalyzeNoteResultDto result) {
        log.info("Received analyze-note result for chat: {}", result.chatId());

        if (!result.success()) {
            String errorMessage = "❌ Failed to analyze note: " + result.errorMessage();
            telegramBot.sendMessage(result.chatId(), errorMessage);
            return;
        }

        try {
            String message = formatResult(result);
            telegramBot.sendMessage(result.chatId(), message);

        } catch (Exception e) {
            log.error("Failed to process analyze-note result for chat {}", result.chatId(), e);
            telegramBot.sendMessage(result.chatId(), "❌ Error processing analysis results");
        }
    }

    private String formatResult(AnalyzeNoteResultDto result) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔍 *").append(result.activeFileName()).append("*\n\n");

        if (result.similarNoteNames() == null || result.similarNoteNames().isEmpty()) {
            sb.append("No related notes found. Try syncing your notes with /sync first.");
            return sb.toString();
        }

        sb.append("пов'язане:\n");
        for (int i = 0; i < result.similarNoteNames().size(); i++) {
            sb.append(i + 1).append(". ").append(result.similarNoteNames().get(i)).append("\n");
        }

        return sb.toString();
    }
}
