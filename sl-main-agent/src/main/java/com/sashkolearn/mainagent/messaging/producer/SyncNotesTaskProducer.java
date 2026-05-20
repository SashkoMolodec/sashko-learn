package com.sashkolearn.mainagent.messaging.producer;

import com.sashkolearn.mainagent.messaging.producer.dto.SyncNotesTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncNotesTaskProducer {

    public static final String TOPIC = "sync-notes-tasks";

    private final KafkaTemplate<String, SyncNotesTaskDto> kafkaTemplate;

    public void send(SyncNotesTaskDto task) {
        String key = task.chatId().toString();
        kafkaTemplate.send(TOPIC, key, task);
        log.info("Sent sync task to Kafka for chat: {}", task.chatId());
    }
}
