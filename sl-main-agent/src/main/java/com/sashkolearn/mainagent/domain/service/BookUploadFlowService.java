package com.sashkolearn.mainagent.domain.service;

import com.sashkolearn.mainagent.config.FileStorageConfig;
import com.sashkolearn.mainagent.infrastructure.redis.RedisService;
import com.sashkolearn.mainagent.messaging.producer.ExtractChaptersTaskProducer;
import com.sashkolearn.mainagent.messaging.producer.dto.ExtractChaptersTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookUploadFlowService {

    private final ExtractChaptersTaskProducer extractChaptersProducer;
    private final TelegramClient telegramClient;
    private final FileStorageConfig fileStorageConfig;
    private final SessionManagementService sessionService;
    private final RedisService redisService;

    private static final String BOOK_PREFIX = "book:";
    private static final int BOOK_TTL = 86400; // 24 hours

    public String handleBookUpload(Long chatId, String fileId, String fileName) {
        try {
            log.info("Processing book upload for chat {}: {}", chatId, fileName);

            // Download file from Telegram
            String downloadedPath = downloadTelegramFile(fileId, fileName);

            // Generate unique book ID
            String bookId = generateBookId();

            // Store book metadata in Redis
            Map<String, String> bookMetadata = new HashMap<>();
            bookMetadata.put("fileName", fileName);
            bookMetadata.put("filePath", downloadedPath);
            bookMetadata.put("chatId", chatId.toString());
            bookMetadata.put("uploadedAt", LocalDateTime.now().toString());
            bookMetadata.put("status", "ANALYZING");

            String bookKey = BOOK_PREFIX + bookId;
            redisService.setHash(bookKey, bookMetadata, BOOK_TTL);

            // Save current book in session
            sessionService.saveCurrentBook(chatId, bookId);

            log.info("Book metadata stored in Redis: bookId={}, file={}", bookId, fileName);

            // Send Kafka message to analyze-agent
            ExtractChaptersTaskDto task = ExtractChaptersTaskDto.of(
                chatId, bookId, downloadedPath, fileName
            );
            extractChaptersProducer.send(task);

            return String.format(
                "✅ Book uploaded: *%s*\n\n🔍 Analyzing chapters...",
                extractTitleFromFilename(fileName)
            );

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

    private String downloadTelegramFile(String fileId, String fileName) throws TelegramApiException, IOException {
        // Get file info from Telegram
        GetFile getFile = GetFile.builder()
            .fileId(fileId)
            .build();

        File telegramFile = telegramClient.execute(getFile);

        // Create local file path
        String sanitizedFileName = sanitizeFileName(fileName);
        String localPath = fileStorageConfig.getBasePath() + "/" + UUID.randomUUID() + "_" + sanitizedFileName;

        // Download file
        java.io.File localFile = telegramClient.downloadFile(telegramFile);

        log.info("Downloaded file from Telegram: {} -> {}", fileName, localFile.getAbsolutePath());

        return localFile.getAbsolutePath();
    }

    private String generateBookId() {
        return UUID.randomUUID().toString();
    }

    private String extractTitleFromFilename(String fileName) {
        // Remove .pdf extension and clean up
        String title = fileName.replaceFirst("[.][^.]+$", "");
        title = title.replace("_", " ").replace("-", " ");
        return title;
    }

    private String sanitizeFileName(String fileName) {
        // Remove special characters that might cause issues
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
