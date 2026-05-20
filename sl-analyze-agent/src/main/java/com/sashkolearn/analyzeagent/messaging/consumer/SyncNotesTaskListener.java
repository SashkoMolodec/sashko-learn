package com.sashkolearn.analyzeagent.messaging.consumer;

import com.sashkolearn.analyzeagent.domain.model.FullSyncResult;
import com.sashkolearn.analyzeagent.domain.service.NoteSyncOrchestrator;
import com.sashkolearn.analyzeagent.infrastructure.redis.RedisService;
import com.sashkolearn.analyzeagent.messaging.consumer.dto.SyncNotesTaskDto;
import com.sashkolearn.analyzeagent.messaging.producer.SyncNotesResultProducer;
import com.sashkolearn.analyzeagent.messaging.producer.dto.SyncNotesResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncNotesTaskListener {

    private final NoteSyncOrchestrator noteSyncOrchestrator;
    private final RedisService redisService;
    private final SyncNotesResultProducer resultProducer;

    @KafkaListener(topics = "sync-notes-tasks", groupId = "analyze-agent-group")
    public void handleSyncTask(SyncNotesTaskDto task) {
        log.info("Received sync notes task for chat: {}", task.chatId());

        try {
            // sync (progress stored in Redis)
            FullSyncResult result = noteSyncOrchestrator.performFullSync(
                    progress -> {
                        String progressKey = "sync:progress:" + task.chatId();
                        redisService.setObject(progressKey, progress, 300);
                    }
            );

            // store result in Redis (claim-check pattern)
            String redisKey = "sync:result:" + task.chatId();
            redisService.setObject(redisKey, result, 3600);

            SyncNotesResultDto resultDto = new SyncNotesResultDto(
                    task.chatId(),
                    true,
                    redisKey,
                    null
            );
            resultProducer.send(resultDto);

        } catch (Exception e) {
            log.error("Failed to sync notes for chat {}", task.chatId(), e);

            SyncNotesResultDto errorDto = new SyncNotesResultDto(
                    task.chatId(),
                    false,
                    null,
                    e.getMessage()
            );
            resultProducer.send(errorDto);
        }
    }
}
