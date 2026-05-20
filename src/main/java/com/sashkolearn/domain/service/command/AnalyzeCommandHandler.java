package com.sashkolearn.domain.service.command;

import com.sashkolearn.api.telegram.TelegramChatBot;
import com.sashkolearn.domain.service.NoteAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AnalyzeCommandHandler implements CommandHandler {

    private final TaskExecutor aiExecutor;
    private final NoteAnalysisService noteAnalysisService;
    private final TelegramChatBot bot;

    public AnalyzeCommandHandler(@Qualifier("aiExecutor") TaskExecutor aiExecutor,
                                 NoteAnalysisService noteAnalysisService,
                                 @Lazy TelegramChatBot bot) {
        this.aiExecutor = aiExecutor;
        this.noteAnalysisService = noteAnalysisService;
        this.bot = bot;
    }

    @Override
    public String commandPrefix() {
        return "/analyze";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        log.info("Starting /analyze command for chat {}", chatId);
        aiExecutor.execute(() -> runAnalyze(chatId));
        return "🔍 аналізую...";
    }

    private void runAnalyze(Long chatId) {
        try {
            NoteAnalysisService.AnalyzeResult result = noteAnalysisService.analyzeActiveNote();
            StringBuilder sb = new StringBuilder();
            sb.append("🔍 *").append(result.activeFileName()).append("*\n\n");
            if (result.similarNoteNames() == null || result.similarNoteNames().isEmpty()) {
                sb.append("No related notes found. Try syncing your notes with /sync first.");
            } else {
                sb.append("пов'язане:\n");
                for (int i = 0; i < result.similarNoteNames().size(); i++) {
                    sb.append(i + 1).append(". ").append(result.similarNoteNames().get(i)).append("\n");
                }
            }
            bot.sendMessage(chatId, sb.toString());
        } catch (Exception e) {
            log.error("Failed to analyze note for chat {}", chatId, e);
            bot.sendMessage(chatId, "❌ Failed to analyze note: " + e.getMessage());
        }
    }
}
