package com.sashkolearn.domain.service;

import com.sashkolearn.domain.entity.AiNote;
import com.sashkolearn.domain.repository.AiNoteRepository;
import com.sashkolearn.util.VectorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReadService {

    private final AiNoteRepository aiNoteRepository;
    private final EmbeddingService embeddingService;
    private final AnthropicChatModel anthropicChatModel;

    @Value("${notes.path}")
    private String notesPath;

    private static final String SYSTEM_PROMPT = """
            Ти — асистент для конспектування. Прочитай наданий матеріал і створи
            структурований конспект у форматі Markdown.

            Правила:
            - Перший рядок: # <Назва статті/матеріалу>
            - Далі: ## ключові секції
            - Виділяй важливі терміни жирним **term**
            - Додай секцію ## Ключові висновки в кінці
            - Мова конспекту: відповідає мові оригіналу (якщо стаття англійська — англійська)
            - Ігноруй навігацію, реклами, header/footer — лише суть
            - Обсяг: достатній щоб замінити читання оригіналу
            """;

    public record ReadResult(String noteContent, String noteFileName) {}

    @Transactional
    public ReadResult readAndGenerateNote(String sourceUrl, String fileContent, String fileName) throws Exception {
        String rawContent;
        String sourceLabel;

        if (sourceUrl != null && !sourceUrl.isBlank()) {
            log.info("Fetching URL: {}", sourceUrl);
            rawContent = fetchUrl(sourceUrl);
            sourceLabel = sourceUrl;
        } else {
            rawContent = fileContent;
            sourceLabel = fileName;
        }

        log.info("Calling Claude to generate note from source: {}", sourceLabel);
        String noteContent = callClaudeForNote(rawContent, sourceLabel);

        String title = extractTitle(noteContent);
        String sanitizedFileName = sanitizeFileName(title) + ".md";

        Path aiNotesDir = Path.of(notesPath, "ai_notes");
        Files.createDirectories(aiNotesDir);
        Path filePath = aiNotesDir.resolve(sanitizedFileName);

        Files.writeString(filePath, noteContent);
        log.info("Saved note to: {}", filePath);

        String filePathStr = filePath.toString();
        long fileSize = noteContent.getBytes().length;

        Optional<AiNote> existing = aiNoteRepository.findByFilePath(filePathStr);
        AiNote aiNote;
        if (existing.isPresent()) {
            aiNote = existing.get();
            aiNote.setContent(noteContent);
            aiNote.setFileSize(fileSize);
            aiNote.setSourceUrl(sourceUrl);
            aiNote = aiNoteRepository.save(aiNote);
        } else {
            aiNote = AiNote.builder()
                    .fileName(sanitizedFileName)
                    .filePath(filePathStr)
                    .content(noteContent)
                    .sourceUrl(sourceUrl)
                    .fileSize(fileSize)
                    .build();
            aiNote = aiNoteRepository.save(aiNote);
        }

        try {
            float[] embedding = embeddingService.generateEmbedding(noteContent);
            String embeddingStr = VectorUtils.toVectorString(embedding);
            aiNoteRepository.updateEmbedding(aiNote.getId(), embeddingStr);
            log.info("Embedding saved for ai_note id={}", aiNote.getId());
        } catch (Exception e) {
            log.warn("Failed to generate embedding for ai_note, continuing without it", e);
        }

        return new ReadResult(noteContent, sanitizedFileName);
    }

    private String fetchUrl(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String callClaudeForNote(String content, String sourceLabel) {
        String truncated = content.length() > 80000 ? content.substring(0, 80000) : content;

        String userPrompt = "Source: " + sourceLabel + "\n\nContent:\n" + truncated;

        var systemMessage = new SystemMessage(SYSTEM_PROMPT);
        var userMessage = new UserMessage(userPrompt);
        var prompt = new Prompt(List.of(systemMessage, userMessage));

        var response = anthropicChatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }

    private String extractTitle(String noteContent) {
        for (String line : noteContent.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }
        return "ai_note";
    }

    private String sanitizeFileName(String title) {
        String sanitized = title.toLowerCase()
                .replaceAll("[^a-z0-9а-яіїєґ]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (sanitized.length() > 80) {
            sanitized = sanitized.substring(0, 80);
        }
        if (sanitized.isEmpty()) {
            sanitized = "ai_note";
        }
        return sanitized;
    }
}
