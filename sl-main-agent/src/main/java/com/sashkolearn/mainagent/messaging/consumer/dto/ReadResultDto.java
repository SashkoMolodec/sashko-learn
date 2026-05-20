package com.sashkolearn.mainagent.messaging.consumer.dto;

public record ReadResultDto(Long chatId, boolean success, String noteContent, String noteFileName, String errorMessage) {
}
