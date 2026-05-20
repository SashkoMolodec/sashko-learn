package com.sashkolearn.analyzeagent.messaging.consumer.dto;

public record ExtractChaptersTaskDto(
    Long chatId,
    String bookId,
    String filePath,
    String fileName
) {
    public static ExtractChaptersTaskDto of(Long chatId, String bookId, String filePath, String fileName) {
        return new ExtractChaptersTaskDto(chatId, bookId, filePath, fileName);
    }
}
