package com.sashkolearn.domain.service;

import com.sashkolearn.api.telegram.TelegramChatBot;
import com.sashkolearn.domain.entity.Note;
import com.sashkolearn.domain.repository.NoteRepository;
import com.sashkolearn.domain.service.command.CommandRouter;
import com.sashkolearn.domain.service.command.QuizCommandHandler;
import com.sashkolearn.domain.service.command.ReadCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserInteractionOrchestrator {

    private static final int MAX_NOTE_CONTEXT = 6000;
    private static final Pattern AI_IMG_COMMENT = Pattern.compile("\\n?<!-- ai-img:[\\s\\S]*?-->", Pattern.DOTALL);

    private static final String NOTE_SUMMARY_SYSTEM = """
            Ти — асистент для огляду нотаток. Дай короткий огляд нотатки у 3-5 реченнях:
            що це за тема, ключові концепти або факти, що тут є.
            Форматування для Telegram: *bold* для заголовків, без # headers, без зайвих символів.
            Відповідай тією ж мовою що й нотатка.
            """;

    private final BookUploadFlowService bookUploadFlowService;
    private final SessionManagementService sessionService;
    private final CommandRouter commandRouter;
    private final QuizCommandHandler quizCommandHandler;
    private final ReadCommandHandler readCommandHandler;
    private final QuizFlowService quizFlowService;
    private final NoteRepository noteRepository;
    private final AnthropicChatModel anthropicChatModel;

    @Value("${ai.model.fast}")
    private String fastModel;

    @Qualifier("aiExecutor")
    @Autowired
    private TaskExecutor aiExecutor;

    @Lazy
    @Autowired
    private TelegramChatBot bot;

    public String handleUserRequest(Long chatId, String rawInput) {
        log.info("Handling user request from chat {}: {}", chatId, rawInput);
        return commandRouter.route(chatId, rawInput);
    }

    public String handleCallback(Long chatId, String data) {
        log.info("Handling callback from chat {}: {}", chatId, data);
        if (data.startsWith("quiz:")) {
            return handleQuizCallback(chatId, data);
        }
        if (data.startsWith("find:")) {
            return handleFindCallback(chatId, data);
        }
        return "Unknown action";
    }

    private String handleFindCallback(Long chatId, String data) {
        String[] parts = data.split(":");
        if (parts.length < 3 || !parts[1].equals("open")) return "❌ Невірний формат callback";

        UUID noteId;
        try {
            noteId = UUID.fromString(parts[2]);
        } catch (IllegalArgumentException e) {
            return "❌ Невірний id нотатки";
        }

        Optional<Note> noteOpt = noteRepository.findById(noteId);
        if (noteOpt.isEmpty()) return "❌ Нотатку не знайдено";

        Note note = noteOpt.get();
        aiExecutor.execute(() -> sendNoteSummary(chatId, note));
        return "📄 читаю...";
    }

    private void sendNoteSummary(Long chatId, Note note) {
        try {
            String content = AI_IMG_COMMENT.matcher(note.getContent()).replaceAll("");
            if (content.length() > MAX_NOTE_CONTEXT) {
                content = content.substring(0, MAX_NOTE_CONTEXT);
            }

            var prompt = new Prompt(
                    List.of(new SystemMessage(NOTE_SUMMARY_SYSTEM),
                            new UserMessage("Нотатка *" + note.getFileName() + "*:\n\n" + content)),
                    AnthropicChatOptions.builder().model(fastModel).maxTokens(600).build()
            );

            String summary = anthropicChatModel.call(prompt).getResult().getOutput().getText();
            bot.sendMessage(chatId, "*" + note.getFileName() + "*\n\n" + summary);
        } catch (Exception e) {
            log.error("Failed to summarize note {} for chat {}", note.getFileName(), chatId, e);
            bot.sendMessage(chatId, "❌ Не вдалось прочитати нотатку");
        }
    }

    public void handlePollAnswer(Long userId, String pollId, List<Integer> selectedOptions) {
        log.info("Handling poll answer from user {}: pollId={}, options={}", userId, pollId, selectedOptions);
        quizFlowService.handlePollAnswer(userId, pollId, selectedOptions);
    }

    public String handleFileUpload(Long chatId, String fileId, String fileName) {
        log.info("Handling file upload from chat {}: {}", chatId, fileName);
        return bookUploadFlowService.handleBookUpload(chatId, fileId, fileName);
    }

    public String handleReadWithFile(Long chatId, String fileContent, String fileName) {
        log.info("Starting /read with file for chat {}: {}", chatId, fileName);
        readCommandHandler.submitFileRead(chatId, fileContent, fileName);
        return "📖 читаю...";
    }

    private String handleQuizCallback(Long chatId, String data) {
        String[] parts = data.split(":");
        if (parts.length < 2) {
            return "❌ Невірний формат callback";
        }

        String action = parts[1];
        return switch (action) {
            case "select" -> {
                if (parts.length < 3) yield "❌ Невірний формат callback";
                UUID quizId = UUID.fromString(parts[2]);
                log.info("User selected quiz {} in chat {}", quizId, chatId);
                quizCommandHandler.submitFetchAndStart(chatId, quizId);
                yield "📖 завантажую квіз...";
            }
            case "new" -> {
                String topic = sessionService.getPendingQuizTopic(chatId);
                if (topic == null) yield "❌ Тема не знайдена. Спробуй /quiz <topic> заново.";
                String keyNoteName = sessionService.getPendingQuizKeyNote(chatId);
                String guide = sessionService.getPendingQuizGuide(chatId);
                log.info("User wants new quiz on topic '{}' with keyNote '{}' guide '{}' in chat {}", topic, keyNoteName, guide, chatId);
                quizCommandHandler.submitGenerateAndStart(chatId, topic, keyNoteName, guide);
                yield "🎯 генерую квіз...";
            }
            default -> "❌ Невідома дія";
        };
    }
}
