package com.sashkolearn.mainagent.domain.service.command;

import com.sashkolearn.mainagent.messaging.producer.ReadTaskProducer;
import com.sashkolearn.mainagent.messaging.producer.dto.ReadTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReadCommandHandler implements CommandHandler {

    private final ReadTaskProducer readTaskProducer;

    @Override
    public String commandPrefix() {
        return "/read";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        String url = rawInput.length() > "/read".length()
                ? rawInput.substring("/read".length()).trim()
                : "";

        if (url.isEmpty()) {
            return "надішли URL або файл (.md/.txt)";
        }

        log.info("Starting /read command for chat {} with url: {}", chatId, url);
        readTaskProducer.send(new ReadTaskDto(chatId, url, null, null));
        return "📖 читаю...";
    }
}
