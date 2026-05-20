package com.sashkolearn.analyzeagent.messaging.producer;

import com.sashkolearn.analyzeagent.messaging.producer.dto.AskQuestionResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AskQuestionResultProducer {

    public static final String TOPIC = "ask-question-results";

    private final KafkaTemplate<String, AskQuestionResultDto> kafkaTemplate;

    public void send(AskQuestionResultDto result) {
        String key = result.chatId().toString();
        kafkaTemplate.send(TOPIC, key, result);
        log.info("Sent ask-question result to Kafka for chat: {}", result.chatId());
    }
}
