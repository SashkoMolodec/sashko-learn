package com.sashkolearn.mainagent.messaging.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sashkolearn.mainagent.api.telegram.TelegramChatBot;
import com.sashkolearn.mainagent.infrastructure.redis.RedisService;
import com.sashkolearn.mainagent.messaging.consumer.dto.ExtractChaptersResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExtractChaptersResultListener {

    private final RedisService redisService;
    private final TelegramChatBot telegramBot;

    @KafkaListener(topics = "extract-chapters-results", groupId = "main-agent-group")
    public void handleExtractionResult(ExtractChaptersResultDto result) {
        log.info("Received chapter extraction result for book: {}, success: {}",
                 result.bookId(), result.success());

        if (!result.success()) {
            handleError(result);
            return;
        }

        handleSuccess(result);
    }

    private void handleSuccess(ExtractChaptersResultDto result) {
        // Retrieve full results from Redis
        List<ExtractChaptersResultDto.ChapterInfo> chapters =
            redisService.getObject(result.redisKey(), new TypeReference<>() {});

        if (chapters == null || chapters.isEmpty()) {
            log.error("No chapters found in Redis for key: {}", result.redisKey());
            sendErrorMessage(result.chatId(), "Failed to retrieve chapters from storage");
            return;
        }

        // Build success message
        String message = buildSuccessMessage(result.bookId(), chapters);

        // Send to Telegram
        log.info("Chapter extraction completed for book: {}, found {} chapters",
                 result.bookId(), chapters.size());

        telegramBot.sendMessage(result.chatId(), message);

        // Clean up Redis after successful processing
        redisService.delete(result.redisKey());
    }

    private void handleError(ExtractChaptersResultDto result) {
        log.error("Chapter extraction failed for book: {}, error: {}",
                  result.bookId(), result.errorMessage());

        String message = String.format(
            "❌ Failed to analyze book\n\nError: %s",
            result.errorMessage()
        );

        sendErrorMessage(result.chatId(), message);
    }

    private String buildSuccessMessage(String bookId, List<ExtractChaptersResultDto.ChapterInfo> chapters) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ *Book analyzed successfully!*\n\n");
        sb.append("📖 *Chapters found:*\n\n");

        for (ExtractChaptersResultDto.ChapterInfo chapter : chapters) {
            sb.append(chapter.chapterNumber())
              .append(". ")
              .append(chapter.title());

            if (chapter.pageStart() != null) {
                sb.append(" (page ").append(chapter.pageStart()).append(")");
            }

            sb.append("\n");
        }

        sb.append("\n_Select a chapter to start learning!_");
        return sb.toString();
    }

    private void sendErrorMessage(Long chatId, String message) {
        telegramBot.sendMessage(chatId, message);
    }
}
