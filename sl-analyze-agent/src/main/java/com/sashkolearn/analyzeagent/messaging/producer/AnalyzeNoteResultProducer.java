package com.sashkolearn.analyzeagent.messaging.producer;

import com.sashkolearn.analyzeagent.messaging.producer.dto.AnalyzeNoteResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzeNoteResultProducer {

    public static final String TOPIC = "analyze-note-results";

    private final KafkaTemplate<String, AnalyzeNoteResultDto> kafkaTemplate;

    public void send(AnalyzeNoteResultDto result) {
        String key = result.chatId().toString();
        kafkaTemplate.send(TOPIC, key, result);
        log.info("Sent analyze-note result to Kafka for chat: {}", result.chatId());
    }
}
