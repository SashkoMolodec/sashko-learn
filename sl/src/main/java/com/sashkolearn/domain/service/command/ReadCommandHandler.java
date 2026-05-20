package com.sashkolearn.domain.service.command;

import com.sashkolearn.api.telegram.TelegramChatBot;
import com.sashkolearn.domain.service.ReadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReadCommandHandler implements CommandHandler {

    private final TaskExecutor aiExecutor;
    private final ReadService readService;
    private final TelegramChatBot bot;

    public ReadCommandHandler(@Qualifier("aiExecutor") TaskExecutor aiExecutor,
                              ReadService readService,
                              @Lazy TelegramChatBot bot) {
        this.aiExecutor = aiExecutor;
        this.readService = readService;
        this.bot = bot;
    }

    @Override
    public String commandPrefix() {
        return "/read";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        String url = rawInput.length() > "/read".length()
                ? rawInput.substring("/read".length()).trim()
                : "";

        if (url.isEmpty()) {
            return "надішли URL або файл (.md/.txt)";
        }

        log.info("Starting /read command for chat {} with url: {}", chatId, url);
        aiExecutor.execute(() -> runRead(chatId, url, null, null));
        return "📖 читаю...";
    }

    public void submitFileRead(Long chatId, String fileContent, String fileName) {
        log.info("Starting /read file for chat {} file: {}", chatId, fileName);
        aiExecutor.execute(() -> runRead(chatId, null, fileContent, fileName));
    }

    private void runRead(Long chatId, String sourceUrl, String fileContent, String fileName) {
        try {
            ReadService.ReadResult result = readService.readAndGenerateNote(sourceUrl, fileContent, fileName);
            String header = "📝 *" + result.noteFileName() + "*\n\n";
            bot.sendMessage(chatId, header + result.noteContent());
        } catch (Exception e) {
            log.error("Failed to read for chat {}", chatId, e);
            bot.sendMessage(chatId, "❌ " + e.getMessage());
        }
    }
}
