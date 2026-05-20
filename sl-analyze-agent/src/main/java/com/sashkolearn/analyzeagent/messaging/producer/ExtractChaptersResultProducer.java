package com.sashkolearn.analyzeagent.messaging.producer;

import com.sashkolearn.analyzeagent.messaging.producer.dto.ExtractChaptersResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExtractChaptersResultProducer {

    public static final String TOPIC = "extract-chapters-results";

    private final KafkaTemplate<String, ExtractChaptersResultDto> kafkaTemplate;

    public void send(ExtractChaptersResultDto result) {
        log.info("Sending chapter extraction result for book: {}, success: {}",
                 result.bookId(), result.success());
        kafkaTemplate.send(TOPIC, result.bookId(), result);
    }
}
