package com.sashkolearn.mainagent.messaging.producer;

import com.sashkolearn.mainagent.messaging.producer.dto.ExtractChaptersTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExtractChaptersTaskProducer {

    public static final String TOPIC = "extract-chapters-tasks";

    private final KafkaTemplate<String, ExtractChaptersTaskDto> kafkaTemplate;

    public void send(ExtractChaptersTaskDto task) {
        log.info("Sending chapter extraction task for book: {}, file: {}", task.bookId(), task.fileName());
        kafkaTemplate.send(TOPIC, task.bookId(), task);
    }
}
