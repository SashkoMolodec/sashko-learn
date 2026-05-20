package com.sashkolearn.analyzeagent.messaging.producer.dto;

public record ReadResultDto(Long chatId, boolean success, String noteContent, String noteFileName, String errorMessage) {
}
