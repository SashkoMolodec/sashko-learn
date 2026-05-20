package com.sashkolearn.analyzeagent.messaging.producer.dto;

public record SyncNotesResultDto(
    Long chatId,
    Boolean success,
    String redisKey,
    String errorMessage
) {
}
