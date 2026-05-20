package com.sashkolearn.analyzeagent.messaging.producer.dto;

import java.util.List;
import java.util.UUID;

public record SearchQuizzesResultDto(
    Long chatId,
    Boolean success,
    List<QuizSummaryDto> quizzes,
    String errorMessage
) {
    public record QuizSummaryDto(
        UUID id,
        String topic,
        String description,
        int questionCount
    ) {}
}
