package com.sashkolearn.analyzeagent.domain.service;

import com.sashkolearn.analyzeagent.infrastructure.redis.RedisService;
import com.sashkolearn.analyzeagent.messaging.consumer.dto.ExtractChaptersTaskDto;
import com.sashkolearn.analyzeagent.messaging.producer.ExtractChaptersResultProducer;
import com.sashkolearn.analyzeagent.messaging.producer.dto.ExtractChaptersResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterExtractionService {

    private final PdfProcessingService pdfProcessingService;
    private final RedisService redisService;
    private final ExtractChaptersResultProducer resultProducer;

    private static final String CHAPTERS_RESULT_PREFIX = "chapters:";
    private static final int REDIS_TTL = 3600; // 1 hour

    public void extractChapters(ExtractChaptersTaskDto task) {
        try {
            log.info("Starting chapter extraction for book: {}, file: {}",
                     task.bookId(), task.fileName());

            // Process PDF
            List<ExtractChaptersResultDto.ChapterInfo> chapters =
                pdfProcessingService.extractChapterTitles(task.filePath());

            log.info("Extracted {} chapters from {}", chapters.size(), task.fileName());

            // Store in Redis with claim-check pattern
            String redisKey = CHAPTERS_RESULT_PREFIX + task.bookId();
            redisService.setObject(redisKey, chapters, REDIS_TTL);

            log.info("Stored chapters in Redis: key={}, chapters={}", redisKey, chapters.size());

            // Send result back to main-agent
            ExtractChaptersResultDto result = new ExtractChaptersResultDto(
                task.chatId(),
                task.bookId(),
                chapters,
                true,
                null,
                redisKey
            );

            resultProducer.send(result);

        } catch (Exception e) {
            log.error("Error extracting chapters from: {}", task.filePath(), e);

            ExtractChaptersResultDto errorResult = new ExtractChaptersResultDto(
                task.chatId(),
                task.bookId(),
                Collections.emptyList(),
                false,
                e.getMessage(),
                null
            );

            resultProducer.send(errorResult);
        }
    }
}
