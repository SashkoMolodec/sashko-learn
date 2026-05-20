package com.sashkolearn.mainagent.domain.service.command;

import com.sashkolearn.mainagent.domain.service.SessionManagementService;
import com.sashkolearn.mainagent.messaging.producer.SearchQuizzesTaskProducer;
import com.sashkolearn.mainagent.messaging.producer.dto.SearchQuizzesTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuizCommandHandler implements CommandHandler {

    private final SearchQuizzesTaskProducer searchQuizzesTaskProducer;
    private final SessionManagementService sessionService;

    @Override
    public String commandPrefix() {
        return "/quiz";
    }

    @Override
    public String execute(Long chatId, String rawInput) {
        String args = rawInput.length() > "/quiz".length()
                ? rawInput.substring("/quiz".length()).trim()
                : "";

        if (args.isEmpty()) {
            return "Usage: /quiz <topic> [-key <noteName>]";
        }

        String topic;
        String keyNoteName = null;

        int keyIndex = args.indexOf(" -key ");
        if (keyIndex != -1) {
            topic = args.substring(0, keyIndex).trim();
            keyNoteName = args.substring(keyIndex + 6).trim();
        } else {
            topic = args;
        }

        if (topic.isEmpty()) {
            return "Usage: /quiz <topic> [-key <noteName>]";
        }

        log.info("Starting /quiz command for chat {} with topic: '{}', keyNote: '{}'", chatId, topic, keyNoteName);

        sessionService.savePendingQuizTopic(chatId, topic);
        sessionService.savePendingQuizKeyNote(chatId, keyNoteName);

        searchQuizzesTaskProducer.send(new SearchQuizzesTaskDto(chatId, topic, keyNoteName));
        return "🔎 шукаю квізи...";
    }
}
