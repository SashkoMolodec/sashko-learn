package com.sashkolearn.analyzeagent.domain.service;

import com.sashkolearn.analyzeagent.domain.entity.Note;
import com.sashkolearn.analyzeagent.domain.repository.NoteRepository;
import com.sashkolearn.analyzeagent.util.VectorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteAnalysisService {

    private static final int SIMILAR_NOTES_LIMIT = 6;
    private static final int RESULT_LIMIT = 5;

    private final ObsidianApiService obsidianApiService;
    private final EmbeddingService embeddingService;
    private final NoteRepository noteRepository;

    public AnalyzeResult analyzeActiveNote() {
        log.info("Starting note analysis");

        ObsidianApiService.ActiveNote activeNote = obsidianApiService.getActiveNote();

        float[] embedding = embeddingService.generateEmbedding(activeNote.content());
        String embeddingStr = VectorUtils.toVectorString(embedding);

        List<Note> similarNotes = noteRepository.findSimilarNotes(embeddingStr, SIMILAR_NOTES_LIMIT);

        List<String> relatedNames = similarNotes.stream()
                .filter(note -> !note.getFileName().equals(activeNote.fileName()))
                .limit(RESULT_LIMIT)
                .map(Note::getFileName)
                .toList();

        log.info("Found {} related notes for {}", relatedNames.size(), activeNote.fileName());
        return new AnalyzeResult(activeNote.fileName(), relatedNames);
    }

    public record AnalyzeResult(
            String activeFileName,
            List<String> similarNoteNames
    ) {
    }
}
