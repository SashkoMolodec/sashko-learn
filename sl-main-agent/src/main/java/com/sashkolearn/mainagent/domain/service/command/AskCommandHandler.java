package com.sashkolearn.mainagent.domain.service.command;

import com.sashkolearn.mainagent.messaging.producer.AskQuestionTaskProducer;
import com.sashkolearn.mainagent.messaging.producer.dto.AskQuestionTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AskCommandHandler implements CommandHandler {

    private final AskQuestionTaskProducer askQuestionTaskProducer;

    @Override
    public String commandPrefix() {
        return "/ask";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        String question = rawInput.length() > "/ask".length()
                ? rawInput.substring("/ask".length()).trim()
                : "";

        if (question.isEmpty()) {
            return "шось спитай";
        }

        log.info("Starting /ask command for chat {} with question: {}", chatId, question);
        askQuestionTaskProducer.send(new AskQuestionTaskDto(chatId, question));
        return "🤔 думаю думу...";
    }
}
