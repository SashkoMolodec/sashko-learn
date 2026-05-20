package com.sashkolearn.domain.service.command;

import com.sashkolearn.api.telegram.TelegramChatBot;
import com.sashkolearn.domain.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class AskCommandHandler implements CommandHandler {

    private static final int SAFE_MESSAGE_LIMIT = 4000;

    private final TaskExecutor aiExecutor;
    private final RagService ragService;
    private final TelegramChatBot bot;

    public AskCommandHandler(@Qualifier("aiExecutor") TaskExecutor aiExecutor,
                             RagService ragService,
                             @Lazy TelegramChatBot bot) {
        this.aiExecutor = aiExecutor;
        this.ragService = ragService;
        this.bot = bot;
    }

    @Override
    public String commandPrefix() {
        return "/ask";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        String question = rawInput.length() > "/ask".length()
                ? rawInput.substring("/ask".length()).trim()
                : "";

        if (question.isEmpty()) {
            return "шось спитай";
        }

        log.info("Starting /ask command for chat {} with question: {}", chatId, question);
        aiExecutor.execute(() -> runAsk(chatId, question));
        return "🤔 думаю думу...";
    }

    private void runAsk(Long chatId, String question) {
        try {
            RagService.RagResult result = ragService.answerQuestion(question);
            for (String part : splitMessage(result.answer())) {
                bot.sendMessage(chatId, part);
            }
            if (result.relevantAttachmentPaths() != null) {
                for (String path : result.relevantAttachmentPaths()) {
                    bot.sendPhoto(chatId, path, null);
                }
            }
        } catch (Exception e) {
            log.error("Failed to answer question for chat {}", chatId, e);
            bot.sendMessage(chatId, "❌ Failed to answer question: " + e.getMessage());
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
            if (splitIndex <= 0) splitIndex = chunk.lastIndexOf("\n");
            if (splitIndex <= 0) splitIndex = SAFE_MESSAGE_LIMIT;
            parts.add(remaining.substring(0, splitIndex).trim());
            remaining = remaining.substring(splitIndex).trim();
        }
        if (!remaining.isEmpty()) parts.add(remaining);
        return parts;
    }
}
