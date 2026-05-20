package com.sashkolearn.analyzeagent.messaging.consumer.dto;

public record FindNotesTaskDto(
    Long chatId,
    String query
) {
}
