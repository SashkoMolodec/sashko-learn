package com.sashkolearn.mainagent.messaging.producer;

import com.sashkolearn.mainagent.messaging.producer.dto.GetQuizTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetQuizTaskProducer {

    public static final String TOPIC = "quiz-get-tasks";

    private final KafkaTemplate<String, GetQuizTaskDto> kafkaTemplate;

    public void send(GetQuizTaskDto task) {
        String key = task.chatId().toString();
        kafkaTemplate.send(TOPIC, key, task);
        log.info("Sent quiz-get task to Kafka for chat: {} with quizId: {}", task.chatId(), task.quizId());
    }
}
