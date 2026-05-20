package com.sashkolearn.mainagent.domain.service.command;

public interface CommandHandler {
    String commandPrefix();
    String execute(Long chatId, String rawInput);
}
