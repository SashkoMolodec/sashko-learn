package com.sashkolearn.analyzeagent.domain.service;

import com.sashkolearn.analyzeagent.util.VectorUtils;
import com.sashkolearn.analyzeagent.config.NotesConfig;
import com.sashkolearn.analyzeagent.domain.entity.Attachment;
import com.sashkolearn.analyzeagent.domain.entity.Note;
import com.sashkolearn.analyzeagent.domain.repository.AttachmentRepository;
import com.sashkolearn.analyzeagent.domain.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteSyncService {

    private final NotesConfig notesConfig;
    private final NoteRepository noteRepository;
    private final AttachmentRepository attachmentRepository;
    private final EmbeddingService embeddingService;
    private final ImageReferenceParserService imageParser;

    @Lazy
    @Autowired
    private NoteSyncService self;

    public SyncResult syncNotes() {
        log.info("Starting notes synchronization from: {}", notesConfig.getPath());

        Path notesPath = Paths.get(notesConfig.getPath());

        if (!Files.exists(notesPath) || !Files.isDirectory(notesPath)) {
            throw new IllegalStateException("Notes directory does not exist: " + notesPath);
        }

        List<Path> markdownFiles = findAllMarkdownFiles(notesPath);
        log.info("Found {} markdown files", markdownFiles.size());

        Set<String> diskFilePaths = markdownFiles.stream()
                .map(path -> path.toAbsolutePath().toString())
                .collect(Collectors.toSet());

        int newNotes = 0;
        int updatedNotes = 0;
        int skippedNotes = 0;
        int errorNotes = 0;
        List<UUID> changedNoteIds = new ArrayList<>();

        for (Path file : markdownFiles) {
            try {
                SyncActionResult actionResult = self.syncSingleNote(file);
                switch (actionResult.action()) {
                    case CREATED -> {
                        newNotes++;
                        changedNoteIds.add(actionResult.noteId());
                    }
                    case UPDATED -> {
                        updatedNotes++;
                        changedNoteIds.add(actionResult.noteId());
                    }
                    case ATTACHMENTS_MISSING -> changedNoteIds.add(actionResult.noteId());
                    case SKIPPED -> skippedNotes++;
                }
            } catch (Exception e) {
                log.error("Failed to sync note: {}", file, e);
                errorNotes++;
            }
        }

        int deletedNotes = self.deleteNotesNotOnDisk(diskFilePaths);

        SyncResult result = new SyncResult(
                markdownFiles.size(),
                newNotes,
                updatedNotes,
                skippedNotes,
                errorNotes,
                deletedNotes,
                changedNoteIds
        );

        log.info("Sync completed: {}", result);
        return result;
    }

    public int generateMissingEmbeddings() {
        List<Note> notesWithoutEmbedding = noteRepository.findNotesWithoutEmbedding();
        log.info("Found {} notes without embeddings", notesWithoutEmbedding.size());

        if (notesWithoutEmbedding.isEmpty()) {
            return 0;
        }

        int batchSize = notesConfig.getSync().getBatchSize();
        int processedCount = 0;

        for (int i = 0; i < notesWithoutEmbedding.size(); i += batchSize) {
            int end = Math.min(i + batchSize, notesWithoutEmbedding.size());
            List<Note> batch = notesWithoutEmbedding.subList(i, end);

            log.info("Processing batch {}/{} (size: {})",
                    (i / batchSize) + 1,
                    (notesWithoutEmbedding.size() + batchSize - 1) / batchSize,
                    batch.size()
            );

            try {
                List<String> texts = batch.stream().map(this::getEnrichedContent).toList();
                // OpenAI API call — outside transaction
                List<float[]> embeddings = embeddingService.generateEmbeddingsBatch(texts);

                for (int j = 0; j < batch.size(); j++) {
                    Note note = batch.get(j);
                    self.saveNoteEmbedding(note.getId(), embeddings.get(j));
                    log.debug("Generated embedding for: {}", note.getFileName());
                }

                processedCount += batch.size();
            } catch (Exception e) {
                log.error("Failed to process batch starting at index {}", i, e);
            }
        }

        log.info("Generated embeddings for {} notes", processedCount);
        return processedCount;
    }

    @Transactional
    public SyncActionResult syncSingleNote(Path file) throws IOException {
        String absolutePath = file.toAbsolutePath().toString();
        String fileName = file.getFileName().toString();
        String content = Files.readString(file);
        long fileSize = Files.size(file);

        var existingNote = noteRepository.findByFilePath(absolutePath);

        if (existingNote.isPresent()) {
            Note note = existingNote.get();

            if (note.getFileSize().equals(fileSize)) {
                if (hasMissingAttachments(note)) {
                    log.info("Note unchanged but has missing attachments, re-queuing: {}", fileName);
                    return new SyncActionResult(SyncAction.ATTACHMENTS_MISSING, note.getId());
                }
                log.debug("Note unchanged, skipping: {}", fileName);
                return new SyncActionResult(SyncAction.SKIPPED, null);
            }

            note.setContent(content);
            note.setFileSize(fileSize);
            noteRepository.save(note);
            noteRepository.clearEmbedding(note.getId());

            log.info("Updated note: {}", fileName);
            return new SyncActionResult(SyncAction.UPDATED, note.getId());
        } else {
            Note newNote = Note.builder()
                    .fileName(fileName)
                    .filePath(absolutePath)
                    .content(content)
                    .fileSize(fileSize)
                    .build();

            noteRepository.save(newNote);
            log.info("Created new note: {}", fileName);
            return new SyncActionResult(SyncAction.CREATED, newNote.getId());
        }
    }

    @Transactional
    public int deleteNotesNotOnDisk(Set<String> diskFilePaths) {
        List<Note> allNotes = noteRepository.findAll();
        List<Note> notesToDelete = new ArrayList<>();

        for (Note note : allNotes) {
            if (!diskFilePaths.contains(note.getFilePath())) {
                notesToDelete.add(note);
                log.info("Note file no longer exists, marking for deletion: {}", note.getFileName());
            }
        }

        if (!notesToDelete.isEmpty()) {
            noteRepository.deleteAll(notesToDelete);
            log.info("Deleted {} notes from database", notesToDelete.size());
        }

        return notesToDelete.size();
    }

    @Transactional
    public void saveNoteEmbedding(UUID noteId, float[] embedding) {
        String embeddingStr = VectorUtils.toVectorString(embedding);
        noteRepository.updateEmbedding(noteId, embeddingStr);
    }

    private List<Path> findAllMarkdownFiles(Path rootPath) {
        List<Path> markdownFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".md"))
                    .forEach(markdownFiles::add);
        } catch (IOException e) {
            log.error("Failed to walk directory tree: {}", rootPath, e);
            throw new RuntimeException("Failed to scan notes directory", e);
        }

        return markdownFiles;
    }

    private String getEnrichedContent(Note note) {
        StringBuilder enriched = new StringBuilder(note.getContent());

        List<Attachment> attachments = attachmentRepository.findByNoteId(note.getId());
        for (Attachment att : attachments) {
            if (att.getDescription() != null && !att.getDescription().isEmpty()) {
                enriched.append("\n\n[Image: ").append(att.getFileName()).append("]\n");
                enriched.append(att.getDescription());
            }
        }

        return enriched.toString();
    }

    private boolean hasMissingAttachments(Note note) {
        List<String> imageRefs = imageParser.extractImageReferences(note.getContent());
        return imageRefs.stream().anyMatch(ref -> !attachmentRepository.existsByFileName(ref));
    }

    private enum SyncAction {
        CREATED, UPDATED, ATTACHMENTS_MISSING, SKIPPED
    }

    public record SyncActionResult(SyncAction action, UUID noteId) {
    }

    public record SyncResult(
            int totalFiles,
            int newNotes,
            int updatedNotes,
            int skippedNotes,
            int errorNotes,
            int deletedNotes,
            List<UUID> changedNoteIds
    ) {
    }
}
