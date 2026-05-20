package com.sashkolearn.analyzeagent.messaging.producer;

import com.sashkolearn.analyzeagent.messaging.producer.dto.GetQuizResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetQuizResultProducer {

    public static final String TOPIC = "quiz-get-results";

    private final KafkaTemplate<String, GetQuizResultDto> kafkaTemplate;

    public void send(GetQuizResultDto result) {
        String key = result.chatId().toString();
        kafkaTemplate.send(TOPIC, key, result);
        log.info("Sent quiz-get result to Kafka for chat: {}", result.chatId());
    }
}
