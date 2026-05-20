package com.sashkolearn.analyzeagent.messaging.producer;

import com.sashkolearn.analyzeagent.messaging.producer.dto.ReadResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReadResultProducer {

    public static final String TOPIC = "read-results";

    private final KafkaTemplate<String, ReadResultDto> kafkaTemplate;

    public void send(ReadResultDto result) {
        String key = result.chatId().toString();
        kafkaTemplate.send(TOPIC, key, result);
        log.info("Sent read-result to Kafka for chat: {}", result.chatId());
    }
}
