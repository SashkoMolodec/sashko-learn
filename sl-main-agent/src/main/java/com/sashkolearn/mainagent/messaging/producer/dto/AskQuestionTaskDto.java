package com.sashkolearn.mainagent.messaging.producer.dto;

public record AskQuestionTaskDto(
    Long chatId,
    String question
) {
}
