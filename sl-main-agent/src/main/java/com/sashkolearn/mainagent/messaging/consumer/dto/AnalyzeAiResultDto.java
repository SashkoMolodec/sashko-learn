package com.sashkolearn.mainagent.messaging.consumer.dto;

public record AnalyzeAiResultDto(
    Long chatId,
    Boolean success,
    String analysis,
    String errorMessage
) {
}
