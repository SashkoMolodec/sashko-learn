package com.sashkolearn.analyzeagent.messaging.consumer;

import com.sashkolearn.analyzeagent.domain.service.QuizGenerationService;
import com.sashkolearn.analyzeagent.messaging.consumer.dto.SearchQuizzesTaskDto;
import com.sashkolearn.analyzeagent.messaging.producer.SearchQuizzesResultProducer;
import com.sashkolearn.analyzeagent.messaging.producer.dto.SearchQuizzesResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SearchQuizzesTaskListener {

    private static final int MAX_RESULTS = 5;

    private final QuizGenerationService quizGenerationService;
    private final SearchQuizzesResultProducer resultProducer;

    @KafkaListener(topics = "quiz-search-tasks", groupId = "analyze-agent-group")
    public void handleSearchQuizzesTask(SearchQuizzesTaskDto task) {
        log.info("Received quiz-search task for chat: {} with topic: {}, keyNote: {}",
                 task.chatId(), task.topic(), task.keyNoteName());

        try {
            List<QuizGenerationService.QuizSummary> summaries = quizGenerationService.searchSimilarQuizzes(task.topic(), MAX_RESULTS);

            List<SearchQuizzesResultDto.QuizSummaryDto> quizDtos = summaries.stream()
                    .map(s -> new SearchQuizzesResultDto.QuizSummaryDto(s.id(), s.topic(), s.description(), s.questionCount()))
                    .toList();

            SearchQuizzesResultDto resultDto = new SearchQuizzesResultDto(
                    task.chatId(),
                    true,
                    quizDtos,
                    null
            );
            resultProducer.send(resultDto);

        } catch (Exception e) {
            log.error("Failed to search quizzes for chat {}", task.chatId(), e);

            SearchQuizzesResultDto errorDto = new SearchQuizzesResultDto(
                    task.chatId(),
                    false,
                    List.of(),
                    e.getMessage()
            );
            resultProducer.send(errorDto);
        }
    }
}
