package com.sashkolearn.domain.service;

import com.sashkolearn.util.VectorUtils;
import com.sashkolearn.config.NotesConfig;
import com.sashkolearn.domain.entity.Attachment;
import com.sashkolearn.domain.entity.Note;
import com.sashkolearn.domain.repository.AttachmentRepository;
import com.sashkolearn.domain.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteSyncService {

    private final NotesConfig notesConfig;
    private final NoteRepository noteRepository;
    private final AttachmentRepository attachmentRepository;
    private final EmbeddingService embeddingService;
    private final ImageReferenceParserService imageParser;
    private final ObsidianApiService obsidianApiService;

    @Lazy
    @Autowired
    private NoteSyncService self;

    public SyncResult syncNotes() {
        log.info("Starting notes synchronization from Obsidian vault folder '{}'", notesConfig.getNotesFolder());

        List<String> markdownPaths = obsidianApiService.listMarkdownFiles();
        log.info("Found {} markdown files", markdownPaths.size());

        Set<String> vaultPaths = new HashSet<>(markdownPaths);

        int newNotes = 0;
        int updatedNotes = 0;
        int skippedNotes = 0;
        int errorNotes = 0;
        List<UUID> changedNoteIds = new ArrayList<>();

        for (String vaultPath : markdownPaths) {
            try {
                // API read happens outside the per-note transaction
                String content = obsidianApiService.readNote(vaultPath);
                SyncActionResult actionResult = self.syncSingleNote(vaultPath, content);
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
                log.error("Failed to sync note: {}", vaultPath, e);
                errorNotes++;
            }
        }

        int deletedNotes = self.deleteNotesNotInVault(vaultPaths);

        SyncResult result = new SyncResult(
                markdownPaths.size(),
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
    public SyncActionResult syncSingleNote(String vaultPath, String content) {
        String fileName = fileNameOf(vaultPath);
        long fileSize = content.getBytes(StandardCharsets.UTF_8).length;

        var existingNote = noteRepository.findByFilePath(vaultPath);

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
                    .filePath(vaultPath)
                    .content(content)
                    .fileSize(fileSize)
                    .build();

            noteRepository.save(newNote);
            log.info("Created new note: {}", fileName);
            return new SyncActionResult(SyncAction.CREATED, newNote.getId());
        }
    }

    @Transactional
    public int deleteNotesNotInVault(Set<String> vaultPaths) {
        List<Note> allNotes = noteRepository.findAll();
        List<Note> notesToDelete = new ArrayList<>();

        for (Note note : allNotes) {
            if (!vaultPaths.contains(note.getFilePath())) {
                notesToDelete.add(note);
                log.info("Note no longer in vault, marking for deletion: {}", note.getFileName());
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

    private static String fileNameOf(String vaultPath) {
        int slash = vaultPath.lastIndexOf('/');
        return slash >= 0 ? vaultPath.substring(slash + 1) : vaultPath;
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
