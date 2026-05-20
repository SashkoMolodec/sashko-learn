package com.sashkolearn.domain.model;

public record ChapterInfo(
    Integer chapterNumber,
    String title,
    Integer pageStart,
    Integer pageEnd
) {}
