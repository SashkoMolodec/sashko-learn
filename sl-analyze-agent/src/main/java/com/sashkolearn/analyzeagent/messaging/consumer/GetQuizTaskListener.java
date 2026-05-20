package com.sashkolearn.analyzeagent.messaging.consumer;

import com.sashkolearn.analyzeagent.domain.entity.Quiz;
import com.sashkolearn.analyzeagent.domain.entity.QuizQuestion;
import com.sashkolearn.analyzeagent.domain.repository.QuizQuestionRepository;
import com.sashkolearn.analyzeagent.domain.repository.QuizRepository;
import com.sashkolearn.analyzeagent.messaging.consumer.dto.GetQuizTaskDto;
import com.sashkolearn.analyzeagent.messaging.producer.GetQuizResultProducer;
import com.sashkolearn.analyzeagent.messaging.producer.dto.GetQuizResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetQuizTaskListener {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final GetQuizResultProducer resultProducer;

    @KafkaListener(topics = "quiz-get-tasks", groupId = "analyze-agent-group")
    public void handleGetQuizTask(GetQuizTaskDto task) {
        log.info("Received quiz-get task for chat: {} with quizId: {}", task.chatId(), task.quizId());

        try {
            Optional<Quiz> quizOpt = quizRepository.findById(task.quizId());
            if (quizOpt.isEmpty()) {
                GetQuizResultDto errorDto = new GetQuizResultDto(
                        task.chatId(),
                        false,
                        task.quizId(),
                        null,
                        null,
                        List.of(),
                        "Quiz not found"
                );
                resultProducer.send(errorDto);
                return;
            }

            Quiz quiz = quizOpt.get();
            List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderByQuestionNumber(task.quizId());

            List<GetQuizResultDto.QuestionDto> questionDtos = questions.stream()
                    .map(q -> new GetQuizResultDto.QuestionDto(
                            q.getQuestionNumber(),
                            q.getQuestion(),
                            q.getOptionA(),
                            q.getOptionB(),
                            q.getOptionC(),
                            q.getOptionD(),
                            q.getCorrectOption(),
                            q.getExplanation()
                    ))
                    .toList();

            GetQuizResultDto resultDto = new GetQuizResultDto(
                    task.chatId(),
                    true,
                    quiz.getId(),
                    quiz.getTopic(),
                    quiz.getDescription(),
                    questionDtos,
                    null
            );
            resultProducer.send(resultDto);

        } catch (Exception e) {
            log.error("Failed to get quiz for chat {}", task.chatId(), e);

            GetQuizResultDto errorDto = new GetQuizResultDto(
                    task.chatId(),
                    false,
                    task.quizId(),
                    null,
                    null,
                    List.of(),
                    e.getMessage()
            );
            resultProducer.send(errorDto);
        }
    }
}
