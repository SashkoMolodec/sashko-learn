package com.sashkolearn.domain.service.command;

import com.sashkolearn.api.telegram.TelegramChatBot;
import com.sashkolearn.domain.entity.Note;
import com.sashkolearn.domain.repository.NoteRepository;
import com.sashkolearn.domain.service.EmbeddingService;
import com.sashkolearn.util.VectorUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
@Slf4j
public class FindCommandHandler implements CommandHandler {

    private static final int TOP_RESULTS = 5;

    private final TaskExecutor aiExecutor;
    private final EmbeddingService embeddingService;
    private final NoteRepository noteRepository;
    private final TelegramChatBot bot;

    public FindCommandHandler(@Qualifier("aiExecutor") TaskExecutor aiExecutor,
                              EmbeddingService embeddingService,
                              NoteRepository noteRepository,
                              @Lazy TelegramChatBot bot) {
        this.aiExecutor = aiExecutor;
        this.embeddingService = embeddingService;
        this.noteRepository = noteRepository;
        this.bot = bot;
    }

    @Override
    public String commandPrefix() {
        return "/find";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        String query = rawInput.length() > "/find".length()
                ? rawInput.substring("/find".length()).trim()
                : "";

        if (query.isEmpty()) {
            return "Usage: /find <query>";
        }

        log.info("Starting /find command for chat {} with query: {}", chatId, query);
        aiExecutor.execute(() -> runFind(chatId, query));
        return "🔎 шукаю...";
    }

    private void runFind(Long chatId, String query) {
        try {
            float[] embedding = embeddingService.generateEmbedding(query);
            List<Note> similar = noteRepository.findSimilarNotes(VectorUtils.toVectorString(embedding), TOP_RESULTS);

            if (similar.isEmpty()) {
                bot.sendMessage(chatId, "😕 Нічого не знайдено. Спробуй /sync спочатку.");
                return;
            }

            List<List<InlineKeyboardButton>> rows = similar.stream()
                    .map(note -> List.of(InlineKeyboardButton.builder()
                            .text(note.getFileName())
                            .callbackData("find:open:" + note.getId())
                            .build()))
                    .toList();

            bot.sendMessageWithKeyboard(chatId, "😎 знайшов:", rows);
        } catch (Exception e) {
            log.error("Failed to find notes for chat {}", chatId, e);
            bot.sendMessage(chatId, "❌ Failed to find notes: " + e.getMessage());
        }
    }
}
