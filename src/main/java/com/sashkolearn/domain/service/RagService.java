package com.sashkolearn.domain.service;

import com.sashkolearn.domain.entity.AiNote;
import com.sashkolearn.domain.entity.Attachment;
import com.sashkolearn.domain.entity.Note;
import com.sashkolearn.domain.repository.AiNoteRepository;
import com.sashkolearn.domain.repository.AttachmentRepository;
import com.sashkolearn.domain.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import com.sashkolearn.util.VectorUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final EmbeddingService embeddingService;
    private final NoteRepository noteRepository;
    private final AiNoteRepository aiNoteRepository;
    private final LinkService linkService;
    private final AttachmentRepository attachmentRepository;
    private final AnthropicChatModel anthropicChatModel;

    @Value("${ai.model.fast}")
    private String fastModel;

    private static final int TOP_SIMILAR_NOTES = 5;
    private static final int MAX_PHOTOS = 3;
    private static final int MAX_CONTEXT_CHARS = 500_000;

    private static final String SYSTEM_PROMPT = """
            CRITICAL FORMATTING RULES — output is displayed in Telegram which has very limited markdown:
            - FORBIDDEN: # headers, ## headers, ### headers — write section titles as *bold text* on its own line instead
            - FORBIDDEN: --- horizontal rules — use blank lines between sections instead
            - FORBIDDEN: tables — use bullet lists with dashes (-) instead
            - FORBIDDEN: bold/italic spans that cross paragraph boundaries
            - ALLOWED: *bold* (single asterisks), _italic_ (single underscores), `code`, plain dashes (-) for lists, blank lines
            Violating these rules will produce unreadable output.

            You are a knowledgeable assistant that answers questions based ONLY on the provided context from the user's personal notes.

            Content rules:
            - PRIORITIZE information from the provided context (user's notes)
            - If context does NOT contain enough info, you MAY use your own knowledge but MUST prefix that part with: "⚠️ *Не з нотаток (AI knowledge):*"
            - NEVER add a sources/references/джерела section at the end — source references are added automatically
            - Respond in the SAME LANGUAGE as the question
            - Be concise but thorough
            """;

    public RagResult answerQuestion(String question) {
        log.info("RAG pipeline started for question: {}", question);

        float[] questionEmbedding = embeddingService.generateEmbedding(question);
        String embeddingStr = VectorUtils.toVectorString(questionEmbedding);

        List<Note> similarNotes = noteRepository.findSimilarNotes(embeddingStr, TOP_SIMILAR_NOTES);
        List<AiNote> similarAiNotes = aiNoteRepository.findSimilarNotes(embeddingStr, 3);
        log.info("Found {} similar notes, {} similar ai_notes", similarNotes.size(), similarAiNotes.size());

        if (similarNotes.isEmpty() && similarAiNotes.isEmpty()) {
            return new RagResult(
                    "нич",
                    List.of(),
                    List.of()
            );
        }

        Set<UUID> contextNoteIds = new LinkedHashSet<>();
        Set<UUID> directSimilarIds = new LinkedHashSet<>();
        for (Note note : similarNotes) {
            directSimilarIds.add(note.getId());
            contextNoteIds.add(note.getId());
        }

        for (Note note : similarNotes) {
            List<Note> related = linkService.findRelatedNotes(note);
            for (Note relatedNote : related) {
                contextNoteIds.add(relatedNote.getId());
            }
        }

        List<Note> contextNotes = contextNoteIds.stream()
                .map(noteRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        log.info("Context expanded to {} notes (from {} similar)", contextNotes.size(), similarNotes.size());

        List<Attachment> allAttachments = new ArrayList<>();
        for (Note note : contextNotes) {
            List<Attachment> noteAttachments = attachmentRepository.findByNoteId(note.getId());
            allAttachments.addAll(noteAttachments);
        }
        log.info("Found {} attachments across context notes", allAttachments.size());

        String contextBlock = buildContextBlock(contextNotes, allAttachments, similarAiNotes);
        String answer = callLlm(question, contextBlock);

        List<String> relevantAttachmentPaths = allAttachments.stream()
                .filter(a -> directSimilarIds.contains(a.getNoteId()))
                .limit(MAX_PHOTOS)
                .map(Attachment::getFilePath)
                .toList();

        List<String> sourceFiles = new ArrayList<>(similarNotes.stream()
                .map(Note::getFileName)
                .toList());
        similarAiNotes.stream().map(AiNote::getFileName).forEach(sourceFiles::add);
        String fullAnswer = appendSources(answer, sourceFiles);

        log.info("RAG pipeline completed. Answer length: {}, sources: {}, attachments: {}",
                fullAnswer.length(), sourceFiles.size(), relevantAttachmentPaths.size());

        return new RagResult(fullAnswer, sourceFiles, relevantAttachmentPaths);
    }

    private String buildContextBlock(List<Note> notes, List<Attachment> attachments, List<AiNote> aiNotes) {
        StringBuilder sb = new StringBuilder();

        Map<UUID, List<Attachment>> attachmentsByNote = attachments.stream()
                .collect(Collectors.groupingBy(Attachment::getNoteId));

        for (Note note : notes) {
            if (sb.length() >= MAX_CONTEXT_CHARS) break;
            sb.append("--- File: ").append(note.getFileName()).append(" ---\n");
            sb.append(note.getContent()).append("\n");

            List<Attachment> noteAttachments = attachmentsByNote.getOrDefault(note.getId(), List.of());
            if (!noteAttachments.isEmpty()) {
                sb.append("\n[Attachments in this note:]\n");
                for (Attachment att : noteAttachments) {
                    if (att.getDescription() != null && !att.getDescription().isBlank()) {
                        sb.append("- ").append(att.getFileName()).append(": ").append(att.getDescription()).append("\n");
                    }
                }
            }
            sb.append("\n");
        }

        for (AiNote aiNote : aiNotes) {
            if (sb.length() >= MAX_CONTEXT_CHARS) break;
            sb.append("--- [AI Note: ").append(aiNote.getFileName()).append("] ---\n");
            sb.append(aiNote.getContent()).append("\n\n");
        }

        return sb.toString();
    }

    private String callLlm(String question, String context) {
        String userPrompt = String.format("""
                Context from notes:
                %s
                
                Question: %s
                """, context, question);

        var systemMessage = new SystemMessage(SYSTEM_PROMPT);
        var userMessage = new UserMessage(userPrompt);
        var prompt = new Prompt(
                List.of(systemMessage, userMessage),
                AnthropicChatOptions.builder()
                        .model(fastModel)
                        .build()
        );

        var response = anthropicChatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }

    private String appendSources(String answer, List<String> sourceFiles) {
        if (sourceFiles.isEmpty()) {
            return answer;
        }

        StringBuilder sb = new StringBuilder(answer);
        sb.append("\n\n---\nSources:\n");
        for (String file : sourceFiles) {
            sb.append("- ").append(file).append("\n");
        }
        return sb.toString();
    }

    public record RagResult(
            String answer,
            List<String> sourceFiles,
            List<String> relevantAttachmentPaths
    ) {
    }
}
