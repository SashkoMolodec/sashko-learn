package com.sashkolearn.mainagent.messaging.producer;

import com.sashkolearn.mainagent.messaging.producer.dto.ReadTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReadTaskProducer {

    public static final String TOPIC = "read-tasks";

    private final KafkaTemplate<String, ReadTaskDto> kafkaTemplate;

    public void send(ReadTaskDto task) {
        String key = task.chatId().toString();
        kafkaTemplate.send(TOPIC, key, task);
        log.info("Sent read-task to Kafka for chat: {}", task.chatId());
    }
}
