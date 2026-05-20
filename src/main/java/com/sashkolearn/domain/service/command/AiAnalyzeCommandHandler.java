package com.sashkolearn.domain.service.command;

import com.sashkolearn.api.telegram.TelegramChatBot;
import com.sashkolearn.domain.service.DeepAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AiAnalyzeCommandHandler implements CommandHandler {

    private final TaskExecutor aiExecutor;
    private final DeepAnalysisService deepAnalysisService;
    private final TelegramChatBot bot;

    public AiAnalyzeCommandHandler(@Qualifier("aiExecutor") TaskExecutor aiExecutor,
                                   DeepAnalysisService deepAnalysisService,
                                   @Lazy TelegramChatBot bot) {
        this.aiExecutor = aiExecutor;
        this.deepAnalysisService = deepAnalysisService;
        this.bot = bot;
    }

    @Override
    public String commandPrefix() {
        return "/ai_analyze";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        log.info("Starting /ai_analyze command for chat {}", chatId);
        aiExecutor.execute(() -> runDeepAnalyze(chatId));
        return "🧠 глибоко аналізую...";
    }

    private void runDeepAnalyze(Long chatId) {
        try {
            String analysis = deepAnalysisService.analyzeWithAi();
            bot.sendMessage(chatId, analysis);
        } catch (Exception e) {
            log.error("Failed deep analysis for chat {}", chatId, e);
            bot.sendMessage(chatId, "❌ Failed to analyze: " + e.getMessage());
        }
    }
}
