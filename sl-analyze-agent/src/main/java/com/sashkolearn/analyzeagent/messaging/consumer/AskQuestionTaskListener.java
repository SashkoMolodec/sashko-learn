package com.sashkolearn.analyzeagent.messaging.consumer;

import com.sashkolearn.analyzeagent.domain.service.RagService;
import com.sashkolearn.analyzeagent.messaging.consumer.dto.AskQuestionTaskDto;
import com.sashkolearn.analyzeagent.messaging.producer.AskQuestionResultProducer;
import com.sashkolearn.analyzeagent.messaging.producer.dto.AskQuestionResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AskQuestionTaskListener {

    private final RagService ragService;
    private final AskQuestionResultProducer resultProducer;

    @KafkaListener(topics = "ask-question-tasks", groupId = "analyze-agent-group")
    public void handleAskQuestionTask(AskQuestionTaskDto task) {
        log.info("Received ask-question task for chat: {}", task.chatId());

        try {
            RagService.RagResult ragResult = ragService.answerQuestion(task.question());

            AskQuestionResultDto resultDto = new AskQuestionResultDto(
                    task.chatId(),
                    true,
                    ragResult.answer(),
                    ragResult.sourceFiles(),
                    ragResult.relevantAttachmentPaths(),
                    null
            );
            resultProducer.send(resultDto);

        } catch (Exception e) {
            log.error("Failed to answer question for chat {}", task.chatId(), e);

            AskQuestionResultDto errorDto = new AskQuestionResultDto(
                    task.chatId(),
                    false,
                    null,
                    List.of(),
                    List.of(),
                    e.getMessage()
            );
            resultProducer.send(errorDto);
        }
    }
}
