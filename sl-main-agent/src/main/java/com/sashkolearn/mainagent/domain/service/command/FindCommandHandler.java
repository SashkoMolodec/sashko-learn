package com.sashkolearn.mainagent.domain.service.command;

import com.sashkolearn.mainagent.messaging.producer.FindNotesTaskProducer;
import com.sashkolearn.mainagent.messaging.producer.dto.FindNotesTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FindCommandHandler implements CommandHandler {

    private final FindNotesTaskProducer findNotesTaskProducer;

    @Override
    public String commandPrefix() {
        return "/find";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        String query = rawInput.length() > "/find".length()
                ? rawInput.substring("/find".length()).trim()
                : "";

        if (query.isEmpty()) {
            return "Usage: /find <query>";
        }

        log.info("Starting /find command for chat {} with query: {}", chatId, query);
        findNotesTaskProducer.send(new FindNotesTaskDto(chatId, query));
        return "🔎 шукаю...";
    }
}
