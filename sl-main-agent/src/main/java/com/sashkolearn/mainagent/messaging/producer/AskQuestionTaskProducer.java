package com.sashkolearn.mainagent.messaging.producer;

import com.sashkolearn.mainagent.messaging.producer.dto.AskQuestionTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AskQuestionTaskProducer {

    public static final String TOPIC = "ask-question-tasks";

    private final KafkaTemplate<String, AskQuestionTaskDto> kafkaTemplate;

    public void send(AskQuestionTaskDto task) {
        String key = task.chatId().toString();
        kafkaTemplate.send(TOPIC, key, task);
        log.info("Sent ask-question task to Kafka for chat: {}", task.chatId());
    }
}
