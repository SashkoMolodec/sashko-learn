package com.sashkolearn.analyzeagent.messaging.consumer;

import com.sashkolearn.analyzeagent.domain.service.ChapterExtractionService;
import com.sashkolearn.analyzeagent.messaging.consumer.dto.ExtractChaptersTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExtractChaptersTaskListener {

    private final ChapterExtractionService chapterExtractionService;

    @KafkaListener(topics = "extract-chapters-tasks", groupId = "analyze-agent-group")
    public void handleExtractionTask(ExtractChaptersTaskDto task) {
        log.info("Received chapter extraction task for book: {}, file: {}",
                 task.bookId(), task.fileName());
        chapterExtractionService.extractChapters(task);
    }
}
