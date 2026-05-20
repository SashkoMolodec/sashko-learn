package com.sashkolearn.mainagent.messaging.producer.dto;

public record GenerateQuizTaskDto(
    Long chatId,
    String topic,
    String keyNoteName
) {
}
