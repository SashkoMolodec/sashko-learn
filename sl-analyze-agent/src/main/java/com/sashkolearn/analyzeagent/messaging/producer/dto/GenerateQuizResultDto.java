package com.sashkolearn.analyzeagent.messaging.producer.dto;

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
