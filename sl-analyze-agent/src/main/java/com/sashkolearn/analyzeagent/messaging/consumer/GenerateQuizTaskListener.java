package com.sashkolearn.analyzeagent.messaging.consumer;

import com.sashkolearn.analyzeagent.domain.service.QuizGenerationService;
import com.sashkolearn.analyzeagent.messaging.consumer.dto.GenerateQuizTaskDto;
import com.sashkolearn.analyzeagent.messaging.producer.GenerateQuizResultProducer;
import com.sashkolearn.analyzeagent.messaging.producer.dto.GenerateQuizResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenerateQuizTaskListener {

    private final QuizGenerationService quizGenerationService;
    private final GenerateQuizResultProducer resultProducer;

    @KafkaListener(topics = "quiz-generate-tasks", groupId = "analyze-agent-group")
    public void handleGenerateQuizTask(GenerateQuizTaskDto task) {
        log.info("Received quiz-generate task for chat: {} with topic: {}, keyNote: {}",
                 task.chatId(), task.topic(), task.keyNoteName());

        try {
            QuizGenerationService.GeneratedQuiz result = quizGenerationService.generateQuiz(
                    task.topic(), task.chatId(), task.keyNoteName());

            GenerateQuizResultDto resultDto = new GenerateQuizResultDto(
                    task.chatId(),
                    true,
                    result.quizId(),
                    result.description(),
                    result.questionCount(),
                    null
            );
            resultProducer.send(resultDto);

        } catch (Exception e) {
            log.error("Failed to generate quiz for chat {}", task.chatId(), e);

            GenerateQuizResultDto errorDto = new GenerateQuizResultDto(
                    task.chatId(),
                    false,
                    null,
                    null,
                    null,
                    e.getMessage()
            );
            resultProducer.send(errorDto);
        }
    }
}
