package com.sashkolearn.domain.service;

import com.sashkolearn.domain.entity.Note;
import com.sashkolearn.domain.repository.NoteRepository;
import com.sashkolearn.domain.service.ai.PromptLoader;
import com.sashkolearn.util.VectorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeepAnalysisService {

    private static final int SIMILAR_NOTES_LIMIT = 5;
    private static final int LEVEL_1_LINKS_LIMIT = 3;
    private static final int LEVEL_2_LINKS_LIMIT = 2;

    private final ObsidianApiService obsidianApiService;
    private final EmbeddingService embeddingService;
    private final NoteRepository noteRepository;
    private final WikilinkParserService wikilinkParser;
    private final AnthropicChatModel anthropicChatModel;
    private final PromptLoader prompts;

    @Value("${ai.model.deep}")
    private String deepModel;

    @Value("${ai.model.standard}")
    private String standardModel;

    public String analyzeWithAi() {
        log.info("Starting deep AI analysis");

        ObsidianApiService.ActiveNote activeNote = obsidianApiService.getActiveNote();
        log.info("Active note: {}", activeNote.fileName());

        float[] embedding = embeddingService.generateEmbedding(activeNote.content());
        String embeddingStr = VectorUtils.toVectorString(embedding);

        List<Note> similarNotes = noteRepository.findSimilarNotes(embeddingStr, SIMILAR_NOTES_LIMIT + 1);
        similarNotes = similarNotes.stream()
                .filter(note -> !note.getFileName().equals(activeNote.fileName()))
                .limit(SIMILAR_NOTES_LIMIT)
                .toList();

        log.info("Found {} similar notes", similarNotes.size());

        if (similarNotes.isEmpty()) {
            return "Не знайдено схожих нотаток для аналізу. Спробуй /sync спочатку.";
        }

        Map<String, String> collectedNotes = collectDeepContext(similarNotes);
        log.info("Collected {} notes for context (including linked)", collectedNotes.size());

        // Step 3.5: note pre-analysis
        String noteAnalysis = callClaudeForNotePreAnalysis(activeNote);
        log.info("Got note pre-analysis");

        // Step 4: initial suggestions
        String initialSuggestions = callClaudeForLinkSuggestions(activeNote, buildContextString(collectedNotes), noteAnalysis);
        log.info("Got initial suggestions");

        // Step 5: critique
        String critique = callClaudeForCritique(activeNote, initialSuggestions);
        log.info("Got critique");

        // Step 6: enrich context
        List<String> searchHints = extractSearchHints(critique);
        Set<String> rejectedNames = extractRejectedNames(critique);
        log.info("Search hints: {}, rejected: {}", searchHints.size(), rejectedNames.size());

        if (!searchHints.isEmpty()) {
            enrichContext(searchHints, rejectedNames, collectedNotes, activeNote.fileName());
            log.info("Context enriched to {} notes", collectedNotes.size());
        }

        // Step 7: refined suggestions
        String refinedSuggestions = callClaudeForRefinedSuggestions(activeNote, collectedNotes, critique, noteAnalysis);
        log.info("Got refined suggestions");

        // Step 8: final critic evaluation
        String finalEval = callClaudeForFinalEvaluation(activeNote, refinedSuggestions);
        log.info("Got final evaluation");

        // Step 9: compose final response
        return formatFinalResponse(activeNote.fileName(), refinedSuggestions, finalEval, collectedNotes.size(), noteAnalysis);
    }

    private Map<String, String> collectDeepContext(List<Note> similarNotes) {
        Map<String, String> contextNotes = new LinkedHashMap<>();

        for (Note similarNote : similarNotes) {
            contextNotes.put(similarNote.getFileName(), similarNote.getContent());

            List<String> level1Links = wikilinkParser.extractWikilinks(similarNote.getContent());
            int level1Count = 0;

            for (String level1LinkName : level1Links) {
                if (level1Count >= LEVEL_1_LINKS_LIMIT) break;
                if (contextNotes.containsKey(level1LinkName)) continue;

                Optional<Note> level1Note = noteRepository.findByFileName(level1LinkName);
                if (level1Note.isEmpty()) continue;

                contextNotes.put(level1Note.get().getFileName(), level1Note.get().getContent());
                level1Count++;

                List<String> level2Links = wikilinkParser.extractWikilinks(level1Note.get().getContent());
                int level2Count = 0;

                for (String level2LinkName : level2Links) {
                    if (level2Count >= LEVEL_2_LINKS_LIMIT) break;
                    if (contextNotes.containsKey(level2LinkName)) continue;

                    Optional<Note> level2Note = noteRepository.findByFileName(level2LinkName);
                    if (level2Note.isEmpty()) continue;

                    contextNotes.put(level2Note.get().getFileName(), level2Note.get().getContent());
                    level2Count++;
                }
            }
        }

        return contextNotes;
    }

    private String buildContextString(Map<String, String> notes) {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, String> entry : notes.entrySet()) {
            sb.append("[Note: ").append(entry.getKey()).append("]\n");
            sb.append(entry.getValue()).append("\n\n");
        }

        return sb.toString();
    }

    private String callClaudeForNotePreAnalysis(ObsidianApiService.ActiveNote activeNote) {
        String system = prompts.load("deep-analysis/system-note-analyst.md");
        String user = prompts.render("deep-analysis/user-note-pre-analysis.md", Map.of(
                "fileName", activeNote.fileName(),
                "content", activeNote.content()
        ));
        return callClaude(system, user, deepModel, 2000);
    }

    private String callClaudeForLinkSuggestions(
            ObsidianApiService.ActiveNote activeNote,
            String context,
            String noteAnalysis
    ) {
        String system = prompts.load("deep-analysis/system-link-suggester.md");
        String user = prompts.render("deep-analysis/user-link-suggestions.md", Map.of(
                "noteAnalysis", noteAnalysis,
                "fileName", activeNote.fileName(),
                "content", activeNote.content(),
                "context", context
        ));
        return callClaude(system, user, standardModel, 4000);
    }

    private String callClaudeForCritique(
            ObsidianApiService.ActiveNote activeNote,
            String initialSuggestions
    ) {
        String system = prompts.load("deep-analysis/system-critic.md");
        String user = prompts.render("deep-analysis/user-critique.md", Map.of(
                "fileName", activeNote.fileName(),
                "content", activeNote.content(),
                "initialSuggestions", initialSuggestions
        ));
        return callClaude(system, user, deepModel, 4000);
    }

    private List<String> extractSearchHints(String critique) {
        List<String> hints = new ArrayList<>();
        boolean inSearchSection = false;

        for (String line : critique.lines().toList()) {
            if (line.trim().startsWith("ШУКАТИ:")) {
                inSearchSection = true;
                continue;
            }
            if (inSearchSection) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) break;
                if (trimmed.startsWith("ДОБРЕ:") || trimmed.startsWith("ВІДХИЛИТИ:")) break;
                if (trimmed.startsWith("- ")) {
                    hints.add(trimmed.substring(2).trim());
                }
            }
        }

        return hints;
    }

    private Set<String> extractRejectedNames(String critique) {
        Set<String> rejected = new LinkedHashSet<>();
        boolean inRejectSection = false;

        for (String line : critique.lines().toList()) {
            if (line.trim().startsWith("ВІДХИЛИТИ:")) {
                inRejectSection = true;
                continue;
            }
            if (inRejectSection) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) break;
                if (trimmed.startsWith("ДОБРЕ:") || trimmed.startsWith("ШУКАТИ:")) break;
                if (trimmed.startsWith("- ")) {
                    String entry = trimmed.substring(2).trim();
                    int dashIdx = entry.indexOf(" - ");
                    String name = dashIdx >= 0 ? entry.substring(0, dashIdx).trim() : entry;
                    if (!name.isBlank()) rejected.add(name);
                }
            }
        }

        return rejected;
    }

    private void enrichContext(
            List<String> hints,
            Set<String> rejectedNoteNames,
            Map<String, String> contextNotes,
            String activeNoteFileName
    ) {
        for (String hint : hints) {
            // Try direct file name lookup first
            Optional<Note> directMatch = noteRepository.findByFileName(hint);
            if (directMatch.isPresent()) {
                Note note = directMatch.get();
                if (!contextNotes.containsKey(note.getFileName())
                        && !rejectedNoteNames.contains(note.getFileName())
                        && !note.getFileName().equals(activeNoteFileName)) {
                    contextNotes.put(note.getFileName(), note.getContent());
                    log.info("Enriched context with direct match: {}", note.getFileName());
                }
                continue;
            }

            // Fallback: embed hint and find similar notes
            try {
                float[] hintEmbedding = embeddingService.generateEmbedding(hint);
                String hintEmbStr = VectorUtils.toVectorString(hintEmbedding);
                List<Note> similar = noteRepository.findSimilarNotes(hintEmbStr, 2);

                for (Note note : similar) {
                    if (!contextNotes.containsKey(note.getFileName())
                            && !rejectedNoteNames.contains(note.getFileName())
                            && !note.getFileName().equals(activeNoteFileName)) {
                        contextNotes.put(note.getFileName(), note.getContent());
                        log.info("Enriched context via embedding for hint '{}': {}", hint, note.getFileName());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to enrich context for hint '{}': {}", hint, e.getMessage());
            }
        }
    }

    private String callClaudeForRefinedSuggestions(
            ObsidianApiService.ActiveNote activeNote,
            Map<String, String> collectedNotes,
            String critique,
            String noteAnalysis
    ) {
        String system = prompts.load("deep-analysis/system-link-suggester.md");
        String user = prompts.render("deep-analysis/user-refined-suggestions.md", Map.of(
                "noteAnalysis", noteAnalysis,
                "fileName", activeNote.fileName(),
                "content", activeNote.content(),
                "context", buildContextString(collectedNotes),
                "critique", critique
        ));
        return callClaude(system, user, standardModel, 4000);
    }

    private String callClaudeForFinalEvaluation(
            ObsidianApiService.ActiveNote activeNote,
            String refinedSuggestions
    ) {
        String system = prompts.load("deep-analysis/system-critic.md");
        String user = prompts.render("deep-analysis/user-final-eval.md", Map.of(
                "fileName", activeNote.fileName(),
                "content", activeNote.content(),
                "refinedSuggestions", refinedSuggestions
        ));
        return callClaude(system, user, deepModel, 300);
    }

    private String callClaude(String system, String user, String model, int maxTokens) {
        var prompt = new Prompt(
                List.of(new SystemMessage(system), new UserMessage(user)),
                AnthropicChatOptions.builder()
                        .model(model)
                        .maxTokens(maxTokens)
                        .cacheOptions(AnthropicCacheOptions.builder()
                                .strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
                                .build())
                        .build()
        );
        return anthropicChatModel.call(prompt).getResult().getOutput().getText();
    }

    private String formatFinalResponse(
            String fileName,
            String refinedSuggestions,
            String finalEval,
            int totalNotesAnalyzed,
            String noteAnalysis
    ) {
        return String.format("""
                🧠 *аналіз зв'язків для: %s*

                📝 *аналіз нотатки:*
                %s

                ---

                %s

                ---
                _перевірено критиком: %s_
                _проаналізовано %d нотаток · 2 ітерації_""",
                fileName, noteAnalysis, refinedSuggestions, finalEval, totalNotesAnalyzed);
    }
}
