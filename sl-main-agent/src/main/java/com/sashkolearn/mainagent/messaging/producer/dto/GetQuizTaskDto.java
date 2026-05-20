package com.sashkolearn.mainagent.messaging.producer.dto;

import java.util.UUID;

public record GetQuizTaskDto(
    Long chatId,
    UUID quizId
) {
}
