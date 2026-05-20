package com.sashkolearn.analyzeagent.messaging.producer;

import com.sashkolearn.analyzeagent.messaging.producer.dto.SyncNotesResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncNotesResultProducer {

    public static final String TOPIC = "sync-notes-results";

    private final KafkaTemplate<String, SyncNotesResultDto> kafkaTemplate;

    public void send(SyncNotesResultDto result) {
        String key = result.chatId().toString();
        kafkaTemplate.send(TOPIC, key, result);
        log.info("Sent sync result to Kafka for chat: {}", result.chatId());
    }
}
