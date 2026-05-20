package com.sashkolearn.analyzeagent.messaging.consumer.dto;

public record AskQuestionTaskDto(
    Long chatId,
    String question
) {
}
