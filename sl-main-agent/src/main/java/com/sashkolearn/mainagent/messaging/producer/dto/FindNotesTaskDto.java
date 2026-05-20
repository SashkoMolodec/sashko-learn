package com.sashkolearn.mainagent.messaging.producer.dto;

public record FindNotesTaskDto(
    Long chatId,
    String query
) {
}
