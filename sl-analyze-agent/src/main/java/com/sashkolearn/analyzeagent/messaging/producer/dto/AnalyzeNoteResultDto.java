package com.sashkolearn.analyzeagent.messaging.producer.dto;

import java.util.List;

public record AnalyzeNoteResultDto(
    Long chatId,
    Boolean success,
    String activeFileName,
    List<String> similarNoteNames,
    String errorMessage
) {
}
