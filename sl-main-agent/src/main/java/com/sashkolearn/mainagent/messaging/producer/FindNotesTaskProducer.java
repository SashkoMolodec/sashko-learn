package com.sashkolearn.mainagent.messaging.producer;

import com.sashkolearn.mainagent.messaging.producer.dto.FindNotesTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FindNotesTaskProducer {

    public static final String TOPIC = "find-notes-tasks";

    private final KafkaTemplate<String, FindNotesTaskDto> kafkaTemplate;

    public void send(FindNotesTaskDto task) {
        String key = task.chatId().toString();
        kafkaTemplate.send(TOPIC, key, task);
        log.info("Sent find-notes task to Kafka for chat: {}", task.chatId());
    }
}
