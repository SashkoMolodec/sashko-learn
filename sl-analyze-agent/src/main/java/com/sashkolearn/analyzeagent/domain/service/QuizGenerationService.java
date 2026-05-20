package com.sashkolearn.analyzeagent.domain.service;

import com.sashkolearn.analyzeagent.domain.entity.Link;
import com.sashkolearn.analyzeagent.domain.entity.Note;
import com.sashkolearn.analyzeagent.domain.entity.Quiz;
import com.sashkolearn.analyzeagent.domain.entity.QuizQuestion;
import com.sashkolearn.analyzeagent.domain.repository.LinkRepository;
import com.sashkolearn.analyzeagent.domain.repository.NoteRepository;
import com.sashkolearn.analyzeagent.domain.repository.QuizQuestionRepository;
import com.sashkolearn.analyzeagent.domain.repository.QuizRepository;
import com.sashkolearn.analyzeagent.domain.service.ai.PromptLoader;
import com.sashkolearn.analyzeagent.util.VectorUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
public class QuizGenerationService {

    private static final int TOP_NOTES_FOR_CONTEXT = 15;
    private static final int MAX_KEY_NOTE_CONTEXT = 10;
    private static final double RELEVANCE_THRESHOLD = 0.3;

    private final EmbeddingService embeddingService;
    private final NoteRepository noteRepository;
    private final LinkRepository linkRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final ChatClient chatClient;
    private final PromptLoader prompts;

    @Value("${ai.model.fast}")
    private String fastModel;

    @Value("${ai.model.standard}")
    private String standardModel;

    public QuizGenerationService(EmbeddingService embeddingService,
                                 NoteRepository noteRepository,
                                 LinkRepository linkRepository,
                                 QuizRepository quizRepository,
                                 QuizQuestionRepository quizQuestionRepository,
                                 AnthropicChatModel anthropicChatModel,
                                 PromptLoader prompts) {
        this.embeddingService = embeddingService;
        this.noteRepository = noteRepository;
        this.linkRepository = linkRepository;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.chatClient = ChatClient.builder(anthropicChatModel).build();
        this.prompts = prompts;
    }

    @Transactional
    public GeneratedQuiz generateQuiz(String topic, Long chatId, String keyNoteName) {
        log.info("Generating quiz for topic: {} by chatId: {}, keyNote: {}", topic, chatId, keyNoteName);

        Optional<Quiz> existing = quizRepository.findByTopicIgnoreCase(topic);
        if (existing.isPresent()) {
            Quiz q = existing.get();
            log.info("Quiz for topic '{}' already exists (id: {}), skipping generation", topic, q.getId());
            return new GeneratedQuiz(q.getId(), q.getDescription(), q.getQuestionCount());
        }

        float[] topicEmbedding = embeddingService.generateEmbedding(topic);
        String embeddingStr = VectorUtils.toVectorString(topicEmbedding);

        List<Note> relevantNotes;
        if (keyNoteName != null && !keyNoteName.isBlank()) {
            relevantNotes = findNotesLinkedToKeyNote(keyNoteName, topicEmbedding);
            log.info("Found {} relevant notes linked to key note '{}' for topic: {}",
                     relevantNotes.size(), keyNoteName, topic);
        } else {
            relevantNotes = noteRepository.findSimilarNotes(embeddingStr, TOP_NOTES_FOR_CONTEXT);
            log.info("Found {} similar notes for topic: {}", relevantNotes.size(), topic);
        }

        String notesContext = buildNotesContext(relevantNotes);

        // 1. Topic analysis — Haiku (cheap, structured output)
        String topicAnalysis = callTopicAnalysis(topic, notesContext);
        log.info("Got topic analysis for: {} ({} chars)", topic, topicAnalysis.length());

        // 2. Initial draft — Haiku (cheap output for first pass)
        QuizResponse draft = callDraftGeneration(topic, notesContext, topicAnalysis);
        log.info("Got draft quiz with {} questions", draft.questions().size());

        // 3. Critique + refine — Sonnet (senior editor on the draft)
        QuizResponse finalQuiz = callCritiqueAndRefine(topic, notesContext, topicAnalysis, draft);
        log.info("Got final quiz with {} questions", finalQuiz.questions().size());

        Quiz quiz = Quiz.builder()
                .topic(topic)
                .description(finalQuiz.description())
                .createdByChatId(chatId)
                .questionCount(finalQuiz.questions().size())
                .build();
        quiz = quizRepository.save(quiz);
        log.info("Saved quiz with id: {}", quiz.getId());

        quizRepository.updateEmbedding(quiz.getId(), embeddingStr);

        int questionNumber = 1;
        for (QuestionData q : finalQuiz.questions()) {
            QuizQuestion question = QuizQuestion.builder()
                    .quizId(quiz.getId())
                    .questionNumber(questionNumber++)
                    .question(q.question())
                    .optionA(q.optionA())
                    .optionB(q.optionB())
                    .optionC(q.optionC())
                    .optionD(q.optionD())
                    .correctOption(q.correct())
                    .explanation(q.explanation())
                    .build();
            quizQuestionRepository.save(question);
        }

        log.info("Saved {} questions for quiz {}", finalQuiz.questions().size(), quiz.getId());
        return new GeneratedQuiz(quiz.getId(), finalQuiz.description(), finalQuiz.questions().size());
    }

    public List<QuizSummary> searchSimilarQuizzes(String topic, int limit) {
        float[] topicEmbedding = embeddingService.generateEmbedding(topic);
        String embeddingStr = VectorUtils.toVectorString(topicEmbedding);
        List<Quiz> quizzes = quizRepository.findSimilarQuizzes(embeddingStr, limit);
        return quizzes.stream()
                .map(q -> new QuizSummary(q.getId(), q.getTopic(), q.getDescription(), q.getQuestionCount()))
                .toList();
    }

    private String callTopicAnalysis(String topic, String notesContext) {
        String system = prompts.render("quiz/system-topic-analyst.md", Map.of("notes", notesContext));
        String user = prompts.render("quiz/user-topic-analysis.md", Map.of("topic", topic));

        return chatClient.prompt()
                .system(system)
                .user(user)
                .options(chatOptions(fastModel, 3000))
                .call()
                .content();
    }

    private QuizResponse callDraftGeneration(String topic, String notesContext, String topicAnalysis) {
        String system = prompts.render("quiz/system-generator.md", Map.of("notes", notesContext));
        String user = prompts.render("quiz/user-quiz-generation.md", Map.of(
                "topic", topic,
                "topicAnalysis", topicAnalysis
        ));

        return chatClient.prompt()
                .system(system)
                .user(user)
                .options(chatOptions(fastModel, 16000))
                .call()
                .entity(QuizResponse.class);
    }

    private QuizResponse callCritiqueAndRefine(String topic, String notesContext, String topicAnalysis,
                                                QuizResponse draft) {
        String draftJson = serializeDraft(draft);

        String system = prompts.render("quiz/system-critic.md", Map.of("notes", notesContext));
        String user = prompts.render("quiz/user-critique.md", Map.of(
                "topic", topic,
                "topicAnalysis", topicAnalysis,
                "initialQuizJson", draftJson
        ));

        return chatClient.prompt()
                .system(system)
                .user(user)
                .options(chatOptions(standardModel, 8000))
                .call()
                .entity(QuizResponse.class);
    }

    private String serializeDraft(QuizResponse draft) {
        StringBuilder sb = new StringBuilder();
        sb.append("Опис: ").append(draft.description()).append("\n\n");
        int i = 1;
        for (QuestionData q : draft.questions()) {
            sb.append(i++).append(". ").append(q.question()).append("\n");
            sb.append("   A) ").append(q.optionA()).append("\n");
            sb.append("   B) ").append(q.optionB()).append("\n");
            sb.append("   C) ").append(q.optionC()).append("\n");
            sb.append("   D) ").append(q.optionD()).append("\n");
            sb.append("   Правильна: ").append(q.correct()).append("\n");
            sb.append("   Пояснення: ").append(q.explanation()).append("\n\n");
        }
        return sb.toString();
    }

    private AnthropicChatOptions chatOptions(String model, int maxTokens) {
        return AnthropicChatOptions.builder()
                .model(model)
                .maxTokens(maxTokens)
                .cacheOptions(AnthropicCacheOptions.builder()
                        .strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
                        .build())
                .build();
    }

    private List<Note> findNotesLinkedToKeyNote(String keyNoteName, float[] topicEmbedding) {
        Note keyNote = noteRepository.findByFileName(keyNoteName)
                .orElseThrow(() -> new RuntimeException("Key note not found: " + keyNoteName));
        log.info("Found key note: {} with id: {}", keyNote.getFileName(), keyNote.getId());

        List<Link> outgoingLinks = linkRepository.findByFromId(keyNote.getId());
        List<Link> incomingLinks = linkRepository.findByToId(keyNote.getId());

        Set<UUID> linkedNoteIds = new HashSet<>();
        outgoingLinks.forEach(l -> linkedNoteIds.add(l.getToId()));
        incomingLinks.forEach(l -> linkedNoteIds.add(l.getFromId()));

        Set<UUID> level2NoteIds = new HashSet<>();
        for (UUID noteId : linkedNoteIds) {
            linkRepository.findByFromId(noteId).forEach(l -> level2NoteIds.add(l.getToId()));
        }
        linkedNoteIds.addAll(level2NoteIds);

        List<Note> candidateNotes = new ArrayList<>(noteRepository.findAllById(linkedNoteIds));
        candidateNotes.add(keyNote);
        return filterByRelevance(candidateNotes, topicEmbedding);
    }

    private List<Note> filterByRelevance(List<Note> notes, float[] topicEmbedding) {
        return notes.stream()
                .filter(note -> {
                    float[] noteEmbedding = note.getEmbeddingAsFloats();
                    if (noteEmbedding == null) return false;
                    double similarity = VectorUtils.cosineSimilarity(topicEmbedding, noteEmbedding);
                    return similarity >= RELEVANCE_THRESHOLD;
                })
                .sorted((a, b) -> {
                    double simA = VectorUtils.cosineSimilarity(topicEmbedding, a.getEmbeddingAsFloats());
                    double simB = VectorUtils.cosineSimilarity(topicEmbedding, b.getEmbeddingAsFloats());
                    return Double.compare(simB, simA);
                })
                .limit(MAX_KEY_NOTE_CONTEXT)
                .toList();
    }

    private String buildNotesContext(List<Note> notes) {
        StringBuilder sb = new StringBuilder();
        for (Note note : notes) {
            sb.append("[Note: ").append(note.getFileName()).append("]\n");
            sb.append(note.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    public record QuizResponse(
            String description,
            List<QuestionData> questions
    ) {}

    public record QuestionData(
            String question,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String correct,
            String explanation
    ) {}

    public record GeneratedQuiz(UUID quizId, String description, int questionCount) {}

    public record QuizSummary(UUID id, String topic, String description, int questionCount) {}
}
