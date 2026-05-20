package com.sashkolearn.analyzeagent.messaging.consumer;

import com.sashkolearn.analyzeagent.domain.service.NoteAnalysisService;
import com.sashkolearn.analyzeagent.messaging.consumer.dto.AnalyzeNoteTaskDto;
import com.sashkolearn.analyzeagent.messaging.producer.AnalyzeNoteResultProducer;
import com.sashkolearn.analyzeagent.messaging.producer.dto.AnalyzeNoteResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzeNoteTaskListener {

    private final NoteAnalysisService noteAnalysisService;
    private final AnalyzeNoteResultProducer resultProducer;

    @KafkaListener(topics = "analyze-note-tasks", groupId = "analyze-agent-group")
    public void handleAnalyzeNoteTask(AnalyzeNoteTaskDto task) {
        log.info("Received analyze-note task for chat: {}", task.chatId());

        try {
            NoteAnalysisService.AnalyzeResult result = noteAnalysisService.analyzeActiveNote();

            AnalyzeNoteResultDto resultDto = new AnalyzeNoteResultDto(
                    task.chatId(),
                    true,
                    result.activeFileName(),
                    result.similarNoteNames(),
                    null
            );
            resultProducer.send(resultDto);

        } catch (Exception e) {
            log.error("Failed to analyze note for chat {}", task.chatId(), e);

            AnalyzeNoteResultDto errorDto = new AnalyzeNoteResultDto(
                    task.chatId(),
                    false,
                    null,
                    List.of(),
                    e.getMessage()
            );
            resultProducer.send(errorDto);
        }
    }
}
