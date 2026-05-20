package com.sashkolearn.mainagent.messaging.producer;

import com.sashkolearn.mainagent.messaging.producer.dto.AnalyzeAiTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzeAiTaskProducer {

    public static final String TOPIC = "ai-analyze-tasks";

    private final KafkaTemplate<String, AnalyzeAiTaskDto> kafkaTemplate;

    public void send(AnalyzeAiTaskDto task) {
        String key = task.chatId().toString();
        kafkaTemplate.send(TOPIC, key, task);
        log.info("Sent ai-analyze task to Kafka for chat: {}", task.chatId());
    }
}
