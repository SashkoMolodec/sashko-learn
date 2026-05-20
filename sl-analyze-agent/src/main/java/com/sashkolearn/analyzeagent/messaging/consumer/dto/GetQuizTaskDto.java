package com.sashkolearn.analyzeagent.messaging.consumer.dto;

import java.util.UUID;

public record GetQuizTaskDto(
    Long chatId,
    UUID quizId
) {
}
