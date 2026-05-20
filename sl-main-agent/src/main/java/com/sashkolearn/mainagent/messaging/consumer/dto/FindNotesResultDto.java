package com.sashkolearn.mainagent.messaging.consumer.dto;

import java.util.List;

public record FindNotesResultDto(
    Long chatId,
    Boolean success,
    List<String> noteNames,
    String errorMessage
) {
}
