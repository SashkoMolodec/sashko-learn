package com.sashkolearn.analyzeagent.messaging.consumer.dto;

public record ReadTaskDto(Long chatId, String sourceUrl, String fileContent, String fileName) {
}
