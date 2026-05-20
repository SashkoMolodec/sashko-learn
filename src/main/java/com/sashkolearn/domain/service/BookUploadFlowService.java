package com.sashkolearn.domain.service;

import com.sashkolearn.api.telegram.TelegramChatBot;
import com.sashkolearn.config.FileStorageConfig;
import com.sashkolearn.domain.model.ChapterInfo;
import com.sashkolearn.infrastructure.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class BookUploadFlowService {

    private static final String BOOK_PREFIX = "book:";
    private static final int BOOK_TTL = 86400;

    private final TelegramClient telegramClient;
    private final FileStorageConfig fileStorageConfig;
    private final SessionManagementService sessionService;
    private final RedisService redisService;
    private final ChapterExtractionService chapterExtractionService;
    private final TaskExecutor aiExecutor;
    private final TelegramChatBot bot;

    public BookUploadFlowService(TelegramClient telegramClient,
                                 FileStorageConfig fileStorageConfig,
                                 SessionManagementService sessionService,
                                 RedisService redisService,
                                 ChapterExtractionService chapterExtractionService,
                                 @Qualifier("aiExecutor") TaskExecutor aiExecutor,
                                 @Lazy TelegramChatBot bot) {
        this.telegramClient = telegramClient;
        this.fileStorageConfig = fileStorageConfig;
        this.sessionService = sessionService;
        this.redisService = redisService;
        this.chapterExtractionService = chapterExtractionService;
        this.aiExecutor = aiExecutor;
        this.bot = bot;
    }

    public String handleBookUpload(Long chatId, String fileId, String fileName) {
        try {
            log.info("Processing book upload for chat {}: {}", chatId, fileName);
            String downloadedPath = downloadTelegramFile(fileId, fileName);
            String bookId = UUID.randomUUID().toString();

            Map<String, String> bookMetadata = new HashMap<>();
            bookMetadata.put("fileName", fileName);
            bookMetadata.put("filePath", downloadedPath);
            bookMetadata.put("chatId", chatId.toString());
            bookMetadata.put("uploadedAt", LocalDateTime.now().toString());
            bookMetadata.put("status", "ANALYZING");
            redisService.setHash(BOOK_PREFIX + bookId, bookMetadata, BOOK_TTL);

            sessionService.saveCurrentBook(chatId, bookId);
            log.info("Book metadata stored: bookId={}, file={}", bookId, fileName);

            aiExecutor.execute(() -> runChapterExtraction(chatId, bookId, downloadedPath, fileName));

            return String.format("✅ Book uploaded: *%s*\n\n🔍 Analyzing chapters...",
                    extractTitleFromFilename(fileName));

        } catch (TelegramApiException e) {
            log.error("Failed to download file from Telegram", e);
            return "❌ Failed to download book from Telegram: " + e.getMessage();
        } catch (IOException e) {
            log.error("Failed to save file locally", e);
            return "❌ Failed to save book: " + e.getMessage();
        } catch (Exception e) {
            log.error("Unexpected error handling book upload", e);
            return "❌ Failed to upload book: " + e.getMessage();
        }
    }

    private void runChapterExtraction(Long chatId, String bookId, String filePath, String fileName) {
        try {
            List<ChapterInfo> chapters = chapterExtractionService.extractChapters(filePath);
            bot.sendMessage(chatId, formatChapters(bookId, chapters));
        } catch (Exception e) {
            log.error("Chapter extraction failed for book {}", bookId, e);
            bot.sendMessage(chatId, String.format("❌ Failed to analyze book\n\nError: %s", e.getMessage()));
        }
    }

    private String formatChapters(String bookId, List<ChapterInfo> chapters) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ *Book analyzed successfully!*\n\n");
        sb.append("📖 *Chapters found:*\n\n");
        for (ChapterInfo chapter : chapters) {
            sb.append(chapter.chapterNumber()).append(". ").append(chapter.title());
            if (chapter.pageStart() != null) {
                sb.append(" (page ").append(chapter.pageStart()).append(")");
            }
            sb.append("\n");
        }
        sb.append("\n_Select a chapter to start learning!_");
        return sb.toString();
    }

    private String downloadTelegramFile(String fileId, String fileName) throws TelegramApiException, IOException {
        GetFile getFile = GetFile.builder().fileId(fileId).build();
        File telegramFile = telegramClient.execute(getFile);
        java.io.File localFile = telegramClient.downloadFile(telegramFile);
        log.info("Downloaded file from Telegram: {} -> {}", fileName, localFile.getAbsolutePath());
        return localFile.getAbsolutePath();
    }

    private String extractTitleFromFilename(String fileName) {
        String title = fileName.replaceFirst("[.][^.]+$", "");
        return title.replace("_", " ").replace("-", " ");
    }
}
