package com.sashkolearn.analyzeagent.messaging.producer.dto;

public record AnalyzeAiResultDto(
    Long chatId,
    Boolean success,
    String analysis,
    String errorMessage
) {
}
