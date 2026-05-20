package com.sashkolearn.mainagent.messaging.consumer;

import com.sashkolearn.mainagent.api.telegram.TelegramChatBot;
import com.sashkolearn.mainagent.messaging.consumer.dto.ReadResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReadResultListener {

    private final TelegramChatBot telegramBot;

    @KafkaListener(topics = "read-results", groupId = "main-agent-group")
    public void handle(ReadResultDto result) {
        log.info("Received read-result for chat: {}", result.chatId());

        if (!result.success()) {
            telegramBot.sendMessage(result.chatId(), "❌ " + result.errorMessage());
            return;
        }

        String header = "📝 *" + result.noteFileName() + "*\n\n";
        telegramBot.sendMessage(result.chatId(), header + result.noteContent());
    }
}
