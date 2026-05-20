package com.sashkolearn.mainagent.domain.service.command;

import com.sashkolearn.mainagent.messaging.producer.SyncNotesTaskProducer;
import com.sashkolearn.mainagent.messaging.producer.dto.SyncNotesTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncCommandHandler implements CommandHandler {

    private final SyncNotesTaskProducer syncNotesTaskProducer;

    @Override
    public String commandPrefix() {
        return "/sync";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        log.info("Starting /sync command for chat {}", chatId);
        syncNotesTaskProducer.send(new SyncNotesTaskDto(chatId));
        return "🔄 cинхронізуєси...";
    }
}
