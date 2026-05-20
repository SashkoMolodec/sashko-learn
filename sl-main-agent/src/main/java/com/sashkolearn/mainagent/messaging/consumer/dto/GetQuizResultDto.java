package com.sashkolearn.mainagent.messaging.consumer.dto;

import java.util.List;
import java.util.UUID;

public record GetQuizResultDto(
    Long chatId,
    Boolean success,
    UUID quizId,
    String topic,
    String description,
    List<QuestionDto> questions,
    String errorMessage
) {
    public record QuestionDto(
        int questionNumber,
        String question,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String correctOption,
        String explanation
    ) {}
}
