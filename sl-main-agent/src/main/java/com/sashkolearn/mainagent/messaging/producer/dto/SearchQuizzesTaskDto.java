package com.sashkolearn.mainagent.messaging.producer.dto;

public record SearchQuizzesTaskDto(
    Long chatId,
    String topic,
    String keyNoteName
) {
}
