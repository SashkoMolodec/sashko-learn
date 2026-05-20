package com.sashkolearn.mainagent.api.telegram;

import com.sashkolearn.mainagent.domain.service.UserInteractionOrchestrator;
import com.sashkolearn.mainagent.infrastructure.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.telegram.telegrambots.meta.api.objects.polls.input.InputPollOption;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.File;
import java.util.List;

@Component
@Slf4j
public class TelegramChatBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final String UPDATE_ID_PREFIX = "tg:update:";
    private static final long UPDATE_ID_TTL_SECONDS = 300; // 5 minutes

    private final String botToken;
    private final TelegramClient telegramClient;
    private final UserInteractionOrchestrator orchestrator;
    private final RedisService redisService;

    public TelegramChatBot(
            @Value("${telegram.bot.token}") String botToken,
            TelegramClient telegramClient,
            UserInteractionOrchestrator orchestrator,
            RedisService redisService
    ) {
        this.botToken = botToken;
        this.telegramClient = telegramClient;
        this.orchestrator = orchestrator;
        this.redisService = redisService;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (isDuplicateUpdate(update.getUpdateId())) {
            log.debug("Skipping duplicate update: {}", update.getUpdateId());
            return;
        }

        try {
            if (update.hasMessage()) {
                long chatId = update.getMessage().getChatId();

                if (update.getMessage().hasDocument()) {
                    String caption = update.getMessage().getCaption();
                    if (caption != null && caption.startsWith("/read")) {
                        handleReadDocument(chatId, update.getMessage().getDocument());
                    } else {
                        handleDocumentUpload(chatId, update.getMessage().getDocument());
                    }
                    return;
                }

                if (update.getMessage().hasText()) {
                    String text = update.getMessage().getText();
                    log.info("📩 Text from [{}]: {}", chatId, text);

                    String response = orchestrator.handleUserRequest(chatId, text);
                    sendMessage(chatId, response);
                }
            } else if (update.hasCallbackQuery()) {
                var callback = update.getCallbackQuery();
                String data = callback.getData();
                long chatId = callback.getMessage().getChatId();
                String queryId = callback.getId();

                log.info("👆 Click from [{}]: {}", chatId, data);

                // Answer callback query to remove loading state
                try {
                    var answerCallback = new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery(queryId);
                    telegramClient.execute(answerCallback);
                } catch (TelegramApiException e) {
                    log.warn("Failed to answer callback query", e);
                }

                String response = orchestrator.handleCallback(chatId, data);
                if (response != null) {
                    sendMessage(chatId, response);
                }
            } else if (update.hasPollAnswer()) {
                var pollAnswer = update.getPollAnswer();
                String pollId = pollAnswer.getPollId();
                long userId = pollAnswer.getUser().getId();
                List<Integer> selectedOptions = pollAnswer.getOptionIds();

                log.info("📊 Poll answer from user [{}] for poll [{}]: {}", userId, pollId, selectedOptions);

                orchestrator.handlePollAnswer(userId, pollId, selectedOptions);
            }
        } catch (Exception e) {
            log.error("Unexpected error in consumer", e);
        }
    }

    private void handleReadDocument(long chatId, Document document) {
        String fileName = document.getFileName();
        if (fileName == null) {
            sendMessage(chatId, "❌ Файл без імені");
            return;
        }
        String lower = fileName.toLowerCase();
        if (!lower.endsWith(".md") && !lower.endsWith(".txt")) {
            sendMessage(chatId, "❌ Підтримуються лише .md і .txt файли");
            return;
        }

        log.info("📄 /read document from [{}]: {}", chatId, fileName);

        try {
            GetFile getFileMethod = new GetFile(document.getFileId());
            org.telegram.telegrambots.meta.api.objects.File tgFile = telegramClient.execute(getFileMethod);
            String fileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + tgFile.getFilePath();

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(fileUrl)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String fileContent = response.body();

            String reply = orchestrator.handleReadWithFile(chatId, fileContent, fileName);
            sendMessage(chatId, reply);
        } catch (Exception e) {
            log.error("Failed to download /read document for chat {}", chatId, e);
            sendMessage(chatId, "❌ Не вдалося завантажити файл: " + e.getMessage());
        }
    }

    private void handleDocumentUpload(long chatId, Document document) {
        String fileName = document.getFileName();

        // Check if it's a PDF
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            sendMessage(chatId, "❌ Please upload a PDF file.");
            return;
        }

        log.info("📄 PDF uploaded from [{}]: {}", chatId, fileName);

        String response = orchestrator.handleFileUpload(chatId, document.getFileId(), fileName);
        sendMessage(chatId, response);
    }

    private static final int TELEGRAM_MESSAGE_LIMIT = 4096;

    public void sendMessage(long chatId, String text) {
        if (text.length() > TELEGRAM_MESSAGE_LIMIT) {
            sendLongMessage(chatId, text);
            return;
        }
        sendSingleMessage(chatId, text);
    }

    private void sendSingleMessage(long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId).text(text).parseMode(ParseMode.MARKDOWN).build());
        } catch (TelegramApiException e) {
            log.warn("Markdown parse failed for chat {}, retrying with escaped text: {}", chatId, e.getMessage());
            try {
                telegramClient.execute(SendMessage.builder()
                        .chatId(chatId).text(escapeTelegramMarkdown(text)).parseMode(ParseMode.MARKDOWN).build());
            } catch (TelegramApiException ex) {
                log.error("Escaped markdown also failed for chat {}, sending plain text: {}", chatId, ex.getMessage());
                try {
                    telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
                } catch (TelegramApiException exc) {
                    log.error("Failed to send plain text message to chat {}", chatId, exc);
                }
            }
        }
    }

    private String escapeTelegramMarkdown(String text) {
        return text
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("`", "\\`")
                .replace("[", "\\[");
    }

    private void sendLongMessage(long chatId, String text) {
        int chunkSize = TELEGRAM_MESSAGE_LIMIT - 50; // leave some margin
        int start = 0;
        int partNumber = 1;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            // Try to split at a newline to avoid breaking mid-sentence
            if (end < text.length()) {
                int lastNewline = text.lastIndexOf('\n', end);
                if (lastNewline > start + chunkSize / 2) {
                    end = lastNewline + 1;
                }
            }

            String chunk = text.substring(start, end);
            sendSingleMessage(chatId, chunk);

            start = end;
            partNumber++;

            // Small delay between messages to avoid rate limiting
            if (start < text.length()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("✅ Sent long message in {} parts to chat {}", partNumber - 1, chatId);
    }

    public void sendPhoto(long chatId, String filePath, String caption) {
        try {
            File photoFile = new File(filePath);
            if (!photoFile.exists()) {
                log.warn("Photo file not found: {}", filePath);
                return;
            }

            SendPhoto.SendPhotoBuilder builder = SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(photoFile));

            if (caption != null && !caption.isEmpty()) {
                builder.caption(caption);
            }

            telegramClient.execute(builder.build());
            log.info("✅ Sent photo to chat {}: {}", chatId, filePath);
        } catch (TelegramApiException e) {
            log.error("Failed to send photo to chat {}: {}", chatId, filePath, e);
        }
    }

    public void sendMessageWithKeyboard(long chatId, String text, List<List<InlineKeyboardButton>> rows) {
        List<InlineKeyboardRow> keyboardRows = rows.stream()
                .map(row -> {
                    InlineKeyboardRow keyboardRow = new InlineKeyboardRow();
                    keyboardRow.addAll(row);
                    return keyboardRow;
                })
                .toList();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder().keyboard(keyboardRows).build();
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId).text(text).replyMarkup(keyboard).parseMode(ParseMode.MARKDOWN).build());
        } catch (TelegramApiException e) {
            log.warn("Markdown parse failed for keyboard message to chat {}, retrying escaped: {}", chatId, e.getMessage());
            try {
                telegramClient.execute(SendMessage.builder()
                        .chatId(chatId).text(escapeTelegramMarkdown(text)).replyMarkup(keyboard).parseMode(ParseMode.MARKDOWN).build());
            } catch (TelegramApiException ex) {
                log.error("Escaped markdown also failed for chat {}, sending plain text: {}", chatId, ex.getMessage());
                try {
                    telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).replyMarkup(keyboard).build());
                } catch (TelegramApiException exc) {
                    log.error("Failed to send keyboard message to chat {}", chatId, exc);
                }
            }
        }
    }

    /**
     * Send a native Telegram quiz poll
     * @return poll ID for tracking answers
     */
    public String sendQuizPoll(long chatId, String question, List<String> options, int correctOptionIndex, String explanation) {
        try {
            List<InputPollOption> pollOptions = options.stream()
                    .map(opt -> (InputPollOption) InputPollOption.builder().text(opt).build())
                    .toList();

            SendPoll.SendPollBuilder builder = SendPoll.builder()
                    .chatId(chatId)
                    .question(question)
                    .options(pollOptions)
                    .type("quiz")
                    .correctOptionId(correctOptionIndex)
                    .isAnonymous(false);

            if (explanation != null && !explanation.isBlank()) {
                builder.explanation(explanation);
                builder.explanationParseMode(ParseMode.MARKDOWN);
            }

            var response = telegramClient.execute(builder.build());
            String pollId = response.getPoll().getId();
            log.info("✅ Sent quiz poll to chat {}, pollId: {}", chatId, pollId);
            return pollId;
        } catch (TelegramApiException e) {
            log.error("Failed to send quiz poll to chat {}", chatId, e);
            return null;
        }
    }

    private boolean isDuplicateUpdate(Integer updateId) {
        String key = UPDATE_ID_PREFIX + updateId;
        return !redisService.setIfAbsent(key, UPDATE_ID_TTL_SECONDS);
    }
}
