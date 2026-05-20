package com.sashkolearn.domain.model;

public record QuizQuestionView(
    int questionNumber,
    String question,
    String optionA,
    String optionB,
    String optionC,
    String optionD,
    String correctOption,
    String explanation
) {}
