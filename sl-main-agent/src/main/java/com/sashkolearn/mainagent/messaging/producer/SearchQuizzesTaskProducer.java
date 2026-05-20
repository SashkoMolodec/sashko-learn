package com.sashkolearn.mainagent.messaging.producer;

import com.sashkolearn.mainagent.messaging.producer.dto.SearchQuizzesTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SearchQuizzesTaskProducer {

    public static final String TOPIC = "quiz-search-tasks";

    private final KafkaTemplate<String, SearchQuizzesTaskDto> kafkaTemplate;

    public void send(SearchQuizzesTaskDto task) {
        String key = task.chatId().toString();
        kafkaTemplate.send(TOPIC, key, task);
        log.info("Sent quiz-search task to Kafka for chat: {} with topic: {}", task.chatId(), task.topic());
    }
}
