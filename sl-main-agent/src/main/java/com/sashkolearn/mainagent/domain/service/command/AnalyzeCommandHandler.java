package com.sashkolearn.mainagent.domain.service.command;

import com.sashkolearn.mainagent.messaging.producer.AnalyzeNoteTaskProducer;
import com.sashkolearn.mainagent.messaging.producer.dto.AnalyzeNoteTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyzeCommandHandler implements CommandHandler {

    private final AnalyzeNoteTaskProducer analyzeNoteTaskProducer;

    @Override
    public String commandPrefix() {
        return "/analyze";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        log.info("Starting /analyze command for chat {}", chatId);
        analyzeNoteTaskProducer.send(new AnalyzeNoteTaskDto(chatId));
        return "🔍 аналізую...";
    }
}
