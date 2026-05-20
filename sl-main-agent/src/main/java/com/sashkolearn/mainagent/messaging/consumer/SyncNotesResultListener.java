package com.sashkolearn.mainagent.messaging.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sashkolearn.mainagent.api.telegram.TelegramChatBot;
import com.sashkolearn.mainagent.domain.model.FullSyncResult;
import com.sashkolearn.mainagent.infrastructure.redis.RedisService;
import com.sashkolearn.mainagent.messaging.consumer.dto.SyncNotesResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncNotesResultListener {

    private final RedisService redisService;
    private final TelegramChatBot telegramBot;

    @KafkaListener(topics = "sync-notes-results", groupId = "main-agent-group")
    public void handleSyncResult(SyncNotesResultDto result) {
        log.info("Received sync result for chat: {}", result.chatId());

        if (!result.success()) {
            String errorMessage = "❌ Sync failed: " + result.errorMessage();
            telegramBot.sendMessage(result.chatId(), errorMessage);
            return;
        }

        try {
            FullSyncResult fullResult = redisService.getObject(
                result.redisKey(),
                new TypeReference<FullSyncResult>() {}
            );

            if (fullResult == null) {
                telegramBot.sendMessage(result.chatId(), "❌ Failed to retrieve sync results from cache");
                return;
            }

            String message = formatSyncResult(fullResult);
            telegramBot.sendMessage(result.chatId(), message);

        } catch (Exception e) {
            log.error("Failed to process sync result for chat {}", result.chatId(), e);
            telegramBot.sendMessage(result.chatId(), "❌ Error processing sync results");
        }
    }

    private String formatSyncResult(FullSyncResult result) {
        return String.format("""
            ✅ синхронізувалось!

            📁 файли: %d всього (%d нові, %d оновлені, %d видалені)
            🤖 ембедінги: %d згенеровано
            🔗 лінки: %d створені (%d поламані)
            """,
            result.syncStats().totalFiles(),
            result.syncStats().newNotes(),
            result.syncStats().updatedNotes(),
            result.syncStats().deletedNotes(),
            result.embeddingsGenerated(),
            result.linkStats().totalLinks(),
            result.linkStats().brokenLinks()
        );
    }
}
