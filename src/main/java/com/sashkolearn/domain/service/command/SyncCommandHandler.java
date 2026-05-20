package com.sashkolearn.domain.service.command;

import com.sashkolearn.api.telegram.TelegramChatBot;
import com.sashkolearn.domain.model.FullSyncResult;
import com.sashkolearn.domain.service.NoteSyncOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SyncCommandHandler implements CommandHandler {

    private final TaskExecutor aiExecutor;
    private final NoteSyncOrchestrator orchestrator;
    private final TelegramChatBot bot;

    public SyncCommandHandler(@Qualifier("aiExecutor") TaskExecutor aiExecutor,
                              NoteSyncOrchestrator orchestrator,
                              @Lazy TelegramChatBot bot) {
        this.aiExecutor = aiExecutor;
        this.orchestrator = orchestrator;
        this.bot = bot;
    }

    @Override
    public String commandPrefix() {
        return "/sync";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        log.info("Starting /sync command for chat {}", chatId);
        aiExecutor.execute(() -> runSync(chatId));
        return "🔄 cинхронізуєси...";
    }

    private void runSync(Long chatId) {
        try {
            FullSyncResult result = orchestrator.performFullSync(progress -> {
                log.debug("sync progress for {}: {}", chatId, progress);
            });
            bot.sendMessage(chatId, formatSyncResult(result));
        } catch (Exception e) {
            log.error("Sync failed for chat {}", chatId, e);
            bot.sendMessage(chatId, "❌ Sync failed: " + e.getMessage());
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
