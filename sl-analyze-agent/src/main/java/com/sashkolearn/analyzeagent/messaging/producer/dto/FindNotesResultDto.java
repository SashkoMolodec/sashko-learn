package com.sashkolearn.analyzeagent.messaging.producer.dto;

import java.util.List;

public record FindNotesResultDto(
    Long chatId,
    Boolean success,
    List<String> noteNames,
    String errorMessage
) {
}
