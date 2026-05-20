package com.sashkolearn.analyzeagent.messaging.producer.dto;

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
