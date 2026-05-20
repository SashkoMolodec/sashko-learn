package com.sashkolearn.analyzeagent.messaging.producer;

import com.sashkolearn.analyzeagent.messaging.producer.dto.GenerateQuizResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenerateQuizResultProducer {

    public static final String TOPIC = "quiz-generate-results";

    private final KafkaTemplate<String, GenerateQuizResultDto> kafkaTemplate;

    public void send(GenerateQuizResultDto result) {
        String key = result.chatId().toString();
        kafkaTemplate.send(TOPIC, key, result);
        log.info("Sent quiz-generate result to Kafka for chat: {}", result.chatId());
    }
}
