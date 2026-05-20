package com.sashkolearn.analyzeagent.messaging.producer;

import com.sashkolearn.analyzeagent.messaging.producer.dto.FindNotesResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FindNotesResultProducer {

    public static final String TOPIC = "find-notes-results";

    private final KafkaTemplate<String, FindNotesResultDto> kafkaTemplate;

    public void send(FindNotesResultDto result) {
        String key = result.chatId().toString();
        kafkaTemplate.send(TOPIC, key, result);
        log.info("Sent find-notes result to Kafka for chat: {}", result.chatId());
    }
}
