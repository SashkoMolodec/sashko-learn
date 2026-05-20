package com.sashkolearn.analyzeagent.messaging.producer.dto;

import java.util.List;

public record ExtractChaptersResultDto(
    Long chatId,
    String bookId,
    List<ChapterInfo> chapters,
    Boolean success,
    String errorMessage,
    String redisKey
) {
    public record ChapterInfo(
        Integer chapterNumber,
        String title,
        Integer pageStart,
        Integer pageEnd
    ) {}
}
