package com.sashkolearn.analyzeagent.messaging.consumer;

import com.sashkolearn.analyzeagent.domain.service.DeepAnalysisService;
import com.sashkolearn.analyzeagent.messaging.consumer.dto.AnalyzeAiTaskDto;
import com.sashkolearn.analyzeagent.messaging.producer.AnalyzeAiResultProducer;
import com.sashkolearn.analyzeagent.messaging.producer.dto.AnalyzeAiResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzeAiTaskListener {

    private final DeepAnalysisService deepAnalysisService;
    private final AnalyzeAiResultProducer resultProducer;

    @KafkaListener(topics = "ai-analyze-tasks", groupId = "analyze-agent-group")
    public void handleAnalyzeAiTask(AnalyzeAiTaskDto task) {
        log.info("Received ai-analyze task for chat: {}", task.chatId());

        try {
            String analysis = deepAnalysisService.analyzeWithAi();

            AnalyzeAiResultDto resultDto = new AnalyzeAiResultDto(
                    task.chatId(),
                    true,
                    analysis,
                    null
            );
            resultProducer.send(resultDto);

        } catch (Exception e) {
            log.error("Failed to perform deep analysis for chat {}", task.chatId(), e);

            AnalyzeAiResultDto errorDto = new AnalyzeAiResultDto(
                    task.chatId(),
                    false,
                    null,
                    e.getMessage()
            );
            resultProducer.send(errorDto);
        }
    }
}
