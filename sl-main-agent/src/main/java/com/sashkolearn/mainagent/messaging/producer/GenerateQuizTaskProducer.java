package com.sashkolearn.mainagent.messaging.producer;

import com.sashkolearn.mainagent.messaging.producer.dto.GenerateQuizTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenerateQuizTaskProducer {

    public static final String TOPIC = "quiz-generate-tasks";

    private final KafkaTemplate<String, GenerateQuizTaskDto> kafkaTemplate;

    public void send(GenerateQuizTaskDto task) {
        String key = task.chatId().toString();
        kafkaTemplate.send(TOPIC, key, task);
        log.info("Sent quiz-generate task to Kafka for chat: {} with topic: {}", task.chatId(), task.topic());
    }
}
