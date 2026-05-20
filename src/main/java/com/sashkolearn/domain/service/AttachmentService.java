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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                    var existing = attachmentRepository.findByFileName(imageFileName);
                    if (existing.isPresent()) {
                        if (existing.get().getDescription() != null) {
                            injectDescriptionIntoNote(note, imageFileName, existing.get().getDescription());
                        }
                        skipped++;
                        continue;
                    }

                    Path imagePath = imgPath.resolve(imageFileName);
                    if (!Files.exists(imagePath)) {
                        log.warn("Image not found: {}", imagePath);
                        errors++;
                        continue;
                    }

                    // Claude API call — outside transaction; returns null if image is too large
                    String description = claudeVisionService.describeImage(imagePath, note.getContent());

                    if (description == null) {
                        skipped++;
                        continue;
                    }

                    // OpenAI API call — outside transaction
                    float[] embedding = null;
                    if (!description.isEmpty()) {
                        embedding = embeddingService.generateEmbedding(description);
                    }

                    // DB writes in own transaction
                    self.saveAttachment(imageFileName, noteId, imagePath, description, embedding);
                    injectDescriptionIntoNote(note, imageFileName, description);

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

    public int reinjectAllDescriptions() {
        List<Attachment> all = attachmentRepository.findAll();
        int injected = 0;
        for (Attachment att : all) {
            if (att.getDescription() == null || att.getDescription().isBlank()) continue;
            var noteOpt = noteRepository.findById(att.getNoteId());
            if (noteOpt.isEmpty()) continue;
            injectDescriptionIntoNote(noteOpt.get(), att.getFileName(), att.getDescription());
            injected++;
        }
        return injected;
    }

    private void injectDescriptionIntoNote(Note note, String imageFileName, String description) {
        Path notePath = Paths.get(note.getFilePath());
        try {
            String content = Files.readString(notePath);
            String marker = "<!-- ai-img: " + imageFileName;
            if (content.contains(marker)) {
                return;
            }
            String escaped = Pattern.quote(imageFileName);
            Pattern pattern = Pattern.compile("!\\[\\[" + escaped + "(?:\\|[^\\]]*)?\\]\\]");
            Matcher matcher = pattern.matcher(content);
            if (!matcher.find()) {
                log.warn("Image reference not found for injection: {} in {}", imageFileName, note.getFileName());
                return;
            }
            String comment = "\n<!-- ai-img: " + imageFileName + "\n" + description + "\n-->";
            String newContent = content.substring(0, matcher.end()) + comment + content.substring(matcher.end());
            Files.writeString(notePath, newContent);
            log.info("Injected description for {} into {}", imageFileName, note.getFileName());
        } catch (IOException e) {
            log.error("Failed to inject description for {} into {}: {}", imageFileName, note.getFileName(), e.getMessage());
        }
    }

    public List<Attachment> getAttachmentsForNote(UUID noteId) {
        return attachmentRepository.findByNoteId(noteId);
    }

    public record AttachmentResult(int processed, int skipped, int errors) {
    }
}
