package com.sashkolearn.analyzeagent.messaging.consumer.dto;

public record GenerateQuizTaskDto(
    Long chatId,
    String topic,
    String keyNoteName
) {
}
