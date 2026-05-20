package com.sashkolearn.mainagent.domain.service;

import com.sashkolearn.mainagent.domain.service.command.CommandRouter;
import com.sashkolearn.mainagent.messaging.producer.GenerateQuizTaskProducer;
import com.sashkolearn.mainagent.messaging.producer.GetQuizTaskProducer;
import com.sashkolearn.mainagent.messaging.producer.ReadTaskProducer;
import com.sashkolearn.mainagent.messaging.producer.dto.GenerateQuizTaskDto;
import com.sashkolearn.mainagent.messaging.producer.dto.GetQuizTaskDto;
import com.sashkolearn.mainagent.messaging.producer.dto.ReadTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserInteractionOrchestrator {

    private final BookUploadFlowService bookUploadFlowService;
    private final SessionManagementService sessionService;
    private final CommandRouter commandRouter;
    private final GenerateQuizTaskProducer generateQuizTaskProducer;
    private final GetQuizTaskProducer getQuizTaskProducer;
    private final ReadTaskProducer readTaskProducer;
    private final QuizFlowService quizFlowService;

    public String handleUserRequest(Long chatId, String rawInput) {
        log.info("Handling user request from chat {}: {}", chatId, rawInput);
        return commandRouter.route(chatId, rawInput);
    }

    public String handleCallback(Long chatId, String data) {
        log.info("Handling callback from chat {}: {}", chatId, data);

        if (data.startsWith("quiz:")) {
            return handleQuizCallback(chatId, data);
        }

        return "Unknown action";
    }

    public void handlePollAnswer(Long userId, String pollId, List<Integer> selectedOptions) {
        log.info("Handling poll answer from user {}: pollId={}, options={}", userId, pollId, selectedOptions);
        quizFlowService.handlePollAnswer(userId, pollId, selectedOptions);
    }

    public String handleFileUpload(Long chatId, String fileId, String fileName) {
        log.info("Handling file upload from chat {}: {}", chatId, fileName);
        return bookUploadFlowService.handleBookUpload(chatId, fileId, fileName);
    }

    public String handleReadWithFile(Long chatId, String fileContent, String fileName) {
        log.info("Starting /read with file for chat {}: {}", chatId, fileName);
        readTaskProducer.send(new ReadTaskDto(chatId, null, fileContent, fileName));
        return "📖 читаю...";
    }

    private String handleQuizCallback(Long chatId, String data) {
        String[] parts = data.split(":");

        if (parts.length < 2) {
            return "❌ Невірний формат callback";
        }

        String action = parts[1];

        return switch (action) {
            case "select" -> {
                if (parts.length < 3) {
                    yield "❌ Невірний формат callback";
                }
                UUID quizId = UUID.fromString(parts[2]);
                log.info("User selected quiz {} in chat {}", quizId, chatId);
                getQuizTaskProducer.send(new GetQuizTaskDto(chatId, quizId));
                yield "📖 завантажую квіз...";
            }
            case "new" -> {
                String topic = sessionService.getPendingQuizTopic(chatId);
                if (topic == null) {
                    yield "❌ Тема не знайдена. Спробуй /quiz <topic> заново.";
                }
                String keyNoteName = sessionService.getPendingQuizKeyNote(chatId);
                log.info("User wants new quiz on topic '{}' with keyNote '{}' in chat {}", topic, keyNoteName, chatId);
                generateQuizTaskProducer.send(new GenerateQuizTaskDto(chatId, topic, keyNoteName));
                yield "🎯 генерую квіз...";
            }
            default -> "❌ Невідома дія";
        };
    }
}
