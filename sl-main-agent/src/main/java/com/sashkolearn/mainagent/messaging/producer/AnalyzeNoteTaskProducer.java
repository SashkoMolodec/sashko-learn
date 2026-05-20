package com.sashkolearn.mainagent.messaging.producer;

import com.sashkolearn.mainagent.messaging.producer.dto.AnalyzeNoteTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzeNoteTaskProducer {

    public static final String TOPIC = "analyze-note-tasks";

    private final KafkaTemplate<String, AnalyzeNoteTaskDto> kafkaTemplate;

    public void send(AnalyzeNoteTaskDto task) {
        String key = task.chatId().toString();
        kafkaTemplate.send(TOPIC, key, task);
        log.info("Sent analyze-note task to Kafka for chat: {}", task.chatId());
    }
}
