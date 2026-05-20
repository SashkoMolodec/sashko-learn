package com.sashkolearn.mainagent.messaging.consumer.dto;

import java.util.List;

public record AskQuestionResultDto(
    Long chatId,
    Boolean success,
    String answer,
    List<String> sourceFiles,
    List<String> relevantAttachmentPaths,
    String errorMessage
) {
}
