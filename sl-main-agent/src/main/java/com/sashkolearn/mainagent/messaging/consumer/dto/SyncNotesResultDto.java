package com.sashkolearn.mainagent.messaging.consumer.dto;

public record SyncNotesResultDto(
    Long chatId,
    Boolean success,
    String redisKey,
    String errorMessage
) {
}
