package com.sashkolearn.domain.service.command;

import com.sashkolearn.api.telegram.TelegramChatBot;
import com.sashkolearn.domain.service.QuizFlowService;
import com.sashkolearn.domain.service.QuizGenerationService;
import com.sashkolearn.domain.service.SessionManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class QuizCommandHandler implements CommandHandler {

    private static final int MAX_RESULTS = 5;

    private final TaskExecutor aiExecutor;
    private final QuizGenerationService quizGenerationService;
    private final QuizFlowService quizFlowService;
    private final SessionManagementService sessionService;
    private final TelegramChatBot bot;

    public QuizCommandHandler(@Qualifier("aiExecutor") TaskExecutor aiExecutor,
                              QuizGenerationService quizGenerationService,
                              QuizFlowService quizFlowService,
                              SessionManagementService sessionService,
                              @Lazy TelegramChatBot bot) {
        this.aiExecutor = aiExecutor;
        this.quizGenerationService = quizGenerationService;
        this.quizFlowService = quizFlowService;
        this.sessionService = sessionService;
        this.bot = bot;
    }

    @Override
    public String commandPrefix() {
        return "/quiz";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        String args = rawInput.length() > "/quiz".length()
                ? rawInput.substring("/quiz".length()).trim()
                : "";

        if (args.isEmpty()) {
            return "Usage: /quiz <topic> [-key <noteName>]";
        }

        String topic;
        String keyNoteName = null;
        String guide = null;

        int keyIndex = args.indexOf(" -key ");
        int guideIndex = args.indexOf(" -guide ");

        String beforeFlags = args;
        if (keyIndex != -1 && (guideIndex == -1 || keyIndex < guideIndex)) {
            beforeFlags = args.substring(0, keyIndex).trim();
            String afterKey = args.substring(keyIndex + 6).trim();
            int guideInAfter = afterKey.indexOf(" -guide ");
            if (guideInAfter != -1) {
                keyNoteName = afterKey.substring(0, guideInAfter).trim();
                guide = afterKey.substring(guideInAfter + 8).trim();
            } else {
                keyNoteName = afterKey;
            }
        } else if (guideIndex != -1) {
            beforeFlags = args.substring(0, guideIndex).trim();
            String afterGuide = args.substring(guideIndex + 8).trim();
            int keyInAfter = afterGuide.indexOf(" -key ");
            if (keyInAfter != -1) {
                guide = afterGuide.substring(0, keyInAfter).trim();
                keyNoteName = afterGuide.substring(keyInAfter + 6).trim();
            } else {
                guide = afterGuide;
            }
        }
        topic = beforeFlags;

        if (topic.isEmpty()) {
            return "Usage: /quiz <topic> [-key <noteName>] [-guide <guideline>]";
        }

        log.info("Starting /quiz for chat {} topic '{}' keyNote '{}' guide '{}'", chatId, topic, keyNoteName, guide);
        sessionService.savePendingQuizTopic(chatId, topic);
        sessionService.savePendingQuizKeyNote(chatId, keyNoteName);
        sessionService.savePendingQuizGuide(chatId, guide);

        final String topicFinal = topic;
        aiExecutor.execute(() -> runSearchQuizzes(chatId, topicFinal));
        return "🔎 шукаю квізи...";
    }

    public void submitGenerateAndStart(Long chatId, String topic, String keyNoteName, String guide) {
        aiExecutor.execute(() -> runGenerateAndStart(chatId, topic, keyNoteName, guide));
    }

    public void submitFetchAndStart(Long chatId, UUID quizId) {
        aiExecutor.execute(() -> runFetchAndStart(chatId, quizId));
    }

    private void runSearchQuizzes(Long chatId, String topic) {
        try {
            List<QuizGenerationService.QuizSummary> summaries =
                    quizGenerationService.searchSimilarQuizzes(topic, MAX_RESULTS);

            StringBuilder sb = new StringBuilder();
            sb.append("🎯 *квізи по темі: ").append(topic).append("*\n\n");

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            if (!summaries.isEmpty()) {
                for (int i = 0; i < summaries.size(); i++) {
                    QuizGenerationService.QuizSummary s = summaries.get(i);
                    sb.append(i + 1).append(". ").append(s.description())
                            .append(" (").append(s.questionCount()).append(" питань)\n");
                    rows.add(List.of(InlineKeyboardButton.builder()
                            .text(String.valueOf(i + 1))
                            .callbackData("quiz:select:" + s.id())
                            .build()));
                }
                sb.append("\n");
            } else {
                sb.append("_нема таких квізів_\n\n");
            }

            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("➕ створити")
                    .callbackData("quiz:new")
                    .build()));

            bot.sendMessageWithKeyboard(chatId, sb.toString(), rows);
        } catch (Exception e) {
            log.error("Failed to search quizzes for chat {}", chatId, e);
            bot.sendMessage(chatId, "❌ Помилка пошуку квізів: " + e.getMessage());
        }
    }

    private void runGenerateAndStart(Long chatId, String topic, String keyNoteName, String guide) {
        try {
            QuizGenerationService.GeneratedQuiz generated =
                    quizGenerationService.generateQuiz(topic, chatId, keyNoteName, guide);

            bot.sendMessage(chatId, String.format(
                    "✅ *Квіз створено!*\n\n_%s_\n\nКількість питань: *%d*\n\nЗавантажую питання...",
                    generated.description(), generated.questionCount()));

            QuizGenerationService.QuizDetails details = quizGenerationService.getQuiz(generated.quizId());
            quizFlowService.startQuiz(chatId, details.quizId(), details.topic(), details.questions());
        } catch (Exception e) {
            log.error("Failed to generate quiz for chat {}", chatId, e);
            bot.sendMessage(chatId, "❌ Помилка генерації квізу: " + e.getMessage());
        }
    }

    private void runFetchAndStart(Long chatId, UUID quizId) {
        try {
            QuizGenerationService.QuizDetails details = quizGenerationService.getQuiz(quizId);
            if (details.questions().isEmpty()) {
                bot.sendMessage(chatId, "❌ Квіз не містить питань");
                return;
            }
            quizFlowService.startQuiz(chatId, details.quizId(), details.topic(), details.questions());
        } catch (Exception e) {
            log.error("Failed to fetch quiz for chat {}", chatId, e);
            bot.sendMessage(chatId, "❌ Помилка завантаження квізу: " + e.getMessage());
        }
    }
}
