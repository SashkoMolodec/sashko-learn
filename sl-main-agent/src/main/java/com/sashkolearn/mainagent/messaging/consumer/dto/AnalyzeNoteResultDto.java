package com.sashkolearn.mainagent.messaging.consumer.dto;

import java.util.List;

public record AnalyzeNoteResultDto(
    Long chatId,
    Boolean success,
    String activeFileName,
    List<String> similarNoteNames,
    String errorMessage
) {
}
