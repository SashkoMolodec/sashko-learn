package com.sashkolearn.mainagent.domain.service.command;

import com.sashkolearn.mainagent.messaging.producer.AnalyzeAiTaskProducer;
import com.sashkolearn.mainagent.messaging.producer.dto.AnalyzeAiTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiAnalyzeCommandHandler implements CommandHandler {

    private final AnalyzeAiTaskProducer analyzeAiTaskProducer;

    @Override
    public String commandPrefix() {
        return "/ai_analyze";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        log.info("Starting /ai_analyze command for chat {}", chatId);
        analyzeAiTaskProducer.send(new AnalyzeAiTaskDto(chatId));
        return "🧠 глибоко аналізую...";
    }
}
