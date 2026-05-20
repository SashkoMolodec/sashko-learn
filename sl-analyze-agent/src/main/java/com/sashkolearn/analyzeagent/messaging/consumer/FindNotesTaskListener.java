package com.sashkolearn.analyzeagent.messaging.consumer;

import com.sashkolearn.analyzeagent.domain.entity.Note;
import com.sashkolearn.analyzeagent.domain.repository.NoteRepository;
import com.sashkolearn.analyzeagent.domain.service.EmbeddingService;
import com.sashkolearn.analyzeagent.messaging.consumer.dto.FindNotesTaskDto;
import com.sashkolearn.analyzeagent.messaging.producer.FindNotesResultProducer;
import com.sashkolearn.analyzeagent.messaging.producer.dto.FindNotesResultDto;
import com.sashkolearn.analyzeagent.util.VectorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FindNotesTaskListener {

    private static final int TOP_RESULTS = 5;

    private final EmbeddingService embeddingService;
    private final NoteRepository noteRepository;
    private final FindNotesResultProducer resultProducer;

    @KafkaListener(topics = "find-notes-tasks", groupId = "analyze-agent-group")
    public void handleFindNotesTask(FindNotesTaskDto task) {
        log.info("Received find-notes task for chat: {} with query: {}", task.chatId(), task.query());

        try {
            float[] embedding = embeddingService.generateEmbedding(task.query());
            String embeddingStr = VectorUtils.toVectorString(embedding);

            List<Note> similarNotes = noteRepository.findSimilarNotes(embeddingStr, TOP_RESULTS);

            List<String> noteNames = similarNotes.stream()
                    .map(Note::getFileName)
                    .toList();

            log.info("Found {} notes for query: {}", noteNames.size(), task.query());

            FindNotesResultDto resultDto = new FindNotesResultDto(
                    task.chatId(),
                    true,
                    noteNames,
                    null
            );
            resultProducer.send(resultDto);

        } catch (Exception e) {
            log.error("Failed to find notes for chat {}", task.chatId(), e);

            FindNotesResultDto errorDto = new FindNotesResultDto(
                    task.chatId(),
                    false,
                    List.of(),
                    e.getMessage()
            );
            resultProducer.send(errorDto);
        }
    }
}
