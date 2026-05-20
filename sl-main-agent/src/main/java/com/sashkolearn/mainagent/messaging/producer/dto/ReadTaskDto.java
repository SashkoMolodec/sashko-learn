package com.sashkolearn.mainagent.messaging.producer.dto;

public record ReadTaskDto(Long chatId, String sourceUrl, String fileContent, String fileName) {
}
