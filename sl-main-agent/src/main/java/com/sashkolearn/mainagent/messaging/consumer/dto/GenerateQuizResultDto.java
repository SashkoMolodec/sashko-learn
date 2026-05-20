package com.sashkolearn.mainagent.messaging.consumer.dto;

import java.util.UUID;

public record GenerateQuizResultDto(
    Long chatId,
    Boolean success,
    UUID quizId,
    String description,
    Integer questionCount,
    String errorMessage
) {
}
