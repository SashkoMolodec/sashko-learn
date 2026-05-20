package com.sashkolearn.analyzeagent.messaging.consumer;

import com.sashkolearn.analyzeagent.domain.service.ReadService;
import com.sashkolearn.analyzeagent.messaging.consumer.dto.ReadTaskDto;
import com.sashkolearn.analyzeagent.messaging.producer.ReadResultProducer;
import com.sashkolearn.analyzeagent.messaging.producer.dto.ReadResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReadTaskListener {

    private final ReadService readService;
    private final ReadResultProducer readResultProducer;

    @KafkaListener(topics = "read-tasks", groupId = "analyze-agent-group")
    public void handle(ReadTaskDto task) {
        log.info("Received read-task for chat: {}", task.chatId());

        try {
            ReadService.ReadResult result = readService.readAndGenerateNote(task);

            readResultProducer.send(new ReadResultDto(
                    task.chatId(),
                    true,
                    result.noteContent(),
                    result.noteFileName(),
                    null
            ));
        } catch (Exception e) {
            log.error("Failed to process read-task for chat {}", task.chatId(), e);

            readResultProducer.send(new ReadResultDto(
                    task.chatId(),
                    false,
                    null,
                    null,
                    e.getMessage()
            ));
        }
    }
}
