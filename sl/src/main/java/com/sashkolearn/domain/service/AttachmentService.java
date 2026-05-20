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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentService {

    private final NotesConfig notesConfig;
    private final NoteRepository noteRepository;
    private final AttachmentRepository attachmentRepository;
    private final ImageReferenceParserService imageParser;
    private final ClaudeVisionService claudeVisionService;
    private final EmbeddingService embeddingService;

    @Lazy
    @Autowired
    private AttachmentService self;

    public AttachmentResult processAttachmentsForNotes(List<UUID> changedNoteIds) {
        log.info("Processing attachments for {} notes", changedNoteIds.size());

        Path notesPath = Paths.get(notesConfig.getPath());
        Path imgPath = notesPath.getParent().resolve("img");

        int processed = 0;
        int skipped = 0;
        int errors = 0;

        for (UUID noteId : changedNoteIds) {
            var noteOpt = noteRepository.findById(noteId);
            if (noteOpt.isEmpty()) {
                log.warn("Note not found: {}", noteId);
                continue;
            }

            Note note = noteOpt.get();
            List<String> imageRefs = imageParser.extractImageReferences(note.getContent());

            for (String imageFileName : imageRefs) {
                try {
                    if (attachmentRepository.existsByFileName(imageFileName)) {
                        log.debug("Image already processed, skipping: {}", imageFileName);
                        skipped++;
                        continue;
                    }

                    Path imagePath = imgPath.resolve(imageFileName);
                    if (!Files.exists(imagePath)) {
                        log.warn("Image not found: {}", imagePath);
                        errors++;
                        continue;
                    }

                    // Claude API call — outside transaction
                    String description = claudeVisionService.describeImage(imagePath, note.getContent());

                    // OpenAI API call — outside transaction
                    float[] embedding = null;
                    if (description != null && !description.isEmpty()) {
                        embedding = embeddingService.generateEmbedding(description);
                    }

                    // DB writes in own transaction
                    self.saveAttachment(imageFileName, noteId, imagePath, description, embedding);

                    processed++;
                    log.info("Processed image: {}", imageFileName);

                } catch (Exception e) {
                    log.error("Failed to process image {}: {}", imageFileName, e.getMessage());
                    errors++;
                }
            }
        }

        AttachmentResult result = new AttachmentResult(processed, skipped, errors);
        log.info("Attachment processing completed: {}", result);
        return result;
    }

    @Transactional
    public void saveAttachment(String imageFileName, UUID noteId, Path imagePath, String description, float[] embedding) {
        Attachment attachment = Attachment.builder()
                .fileName(imageFileName)
                .noteId(noteId)
                .filePath(imagePath.toString())
                .description(description)
                .build();
        attachmentRepository.save(attachment);

        if (embedding != null) {
            String embeddingStr = VectorUtils.toVectorString(embedding);
            attachmentRepository.updateEmbedding(imageFileName, embeddingStr);
        }
    }

    public List<Attachment> getAttachmentsForNote(UUID noteId) {
        return attachmentRepository.findByNoteId(noteId);
    }

    public record AttachmentResult(int processed, int skipped, int errors) {
    }
}
