package com.sashkolearn.mainagent.messaging.consumer;

import com.sashkolearn.mainagent.api.telegram.TelegramChatBot;
import com.sashkolearn.mainagent.domain.service.SessionManagementService;
import com.sashkolearn.mainagent.messaging.consumer.dto.SearchQuizzesResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SearchQuizzesResultListener {

    private final TelegramChatBot telegramBot;
    private final SessionManagementService sessionService;

    @KafkaListener(topics = "quiz-search-results", groupId = "main-agent-group")
    public void handleSearchQuizzesResult(SearchQuizzesResultDto result) {
        log.info("Received quiz-search result for chat: {}", result.chatId());

        if (!result.success()) {
            String errorMessage = "❌ Помилка пошуку квізів: " + result.errorMessage();
            telegramBot.sendMessage(result.chatId(), errorMessage);
            return;
        }

        try {
            String topic = sessionService.getPendingQuizTopic(result.chatId());
            if (topic == null) {
                topic = "квіз";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("🎯 *квізи по темі: ").append(topic).append("*\n\n");

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            if (result.quizzes() != null && !result.quizzes().isEmpty()) {
                for (int i = 0; i < result.quizzes().size(); i++) {
                    SearchQuizzesResultDto.QuizSummaryDto quiz = result.quizzes().get(i);
                    sb.append(i + 1).append(". ").append(quiz.description())
                            .append(" (").append(quiz.questionCount()).append(" питань)\n");

                    // Add button for this quiz (just the number)
                    InlineKeyboardButton btn = InlineKeyboardButton.builder()
                            .text(String.valueOf(i + 1))
                            .callbackData("quiz:select:" + quiz.id())
                            .build();
                    rows.add(List.of(btn));
                }
                sb.append("\n");
            } else {
                sb.append("_нема таких квізів_\n\n");
            }

            // Topic is already stored in session, no need to pass in callback
            InlineKeyboardButton createNewBtn = InlineKeyboardButton.builder()
                    .text("➕ створити")
                    .callbackData("quiz:new")
                    .build();
            rows.add(List.of(createNewBtn));

            telegramBot.sendMessageWithKeyboard(result.chatId(), sb.toString(), rows);

        } catch (Exception e) {
            log.error("Failed to process quiz-search result for chat {}", result.chatId(), e);
            telegramBot.sendMessage(result.chatId(), "❌ Помилка обробки результатів пошуку");
        }
    }
}
