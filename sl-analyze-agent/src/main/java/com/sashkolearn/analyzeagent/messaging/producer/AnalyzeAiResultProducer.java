package com.sashkolearn.analyzeagent.messaging.producer;

import com.sashkolearn.analyzeagent.messaging.producer.dto.AnalyzeAiResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzeAiResultProducer {

    public static final String TOPIC = "ai-analyze-results";

    private final KafkaTemplate<String, AnalyzeAiResultDto> kafkaTemplate;

    public void send(AnalyzeAiResultDto result) {
        String key = result.chatId().toString();
        kafkaTemplate.send(TOPIC, key, result);
        log.info("Sent ai-analyze result to Kafka for chat: {}", result.chatId());
    }
}
