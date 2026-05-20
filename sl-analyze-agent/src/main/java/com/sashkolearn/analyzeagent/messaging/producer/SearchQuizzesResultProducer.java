package com.sashkolearn.analyzeagent.messaging.producer;

import com.sashkolearn.analyzeagent.messaging.producer.dto.SearchQuizzesResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SearchQuizzesResultProducer {

    public static final String TOPIC = "quiz-search-results";

    private final KafkaTemplate<String, SearchQuizzesResultDto> kafkaTemplate;

    public void send(SearchQuizzesResultDto result) {
        String key = result.chatId().toString();
        kafkaTemplate.send(TOPIC, key, result);
        log.info("Sent quiz-search result to Kafka for chat: {}", result.chatId());
    }
}
