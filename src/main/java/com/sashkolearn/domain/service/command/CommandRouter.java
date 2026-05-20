package com.sashkolearn.domain.service.command;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class CommandRouter {

    private final List<CommandHandler> handlers;

    public CommandRouter(List<CommandHandler> handlers) {
        // longer prefix first so /ai_analyze beats /analyze
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt((CommandHandler h) -> h.commandPrefix().length()).reversed())
                .toList();
    }

    public String route(Long chatId, String rawInput) {
        for (CommandHandler handler : handlers) {
            if (rawInput.startsWith(handler.commandPrefix())) {
                return handler.execute(chatId, rawInput);
            }
        }
        return "Unknown command";
    }
}
