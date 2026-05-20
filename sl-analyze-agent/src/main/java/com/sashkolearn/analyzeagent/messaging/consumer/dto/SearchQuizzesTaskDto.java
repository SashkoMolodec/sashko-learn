package com.sashkolearn.analyzeagent.messaging.consumer.dto;

public record SearchQuizzesTaskDto(
    Long chatId,
    String topic,
    String keyNoteName
) {
}
