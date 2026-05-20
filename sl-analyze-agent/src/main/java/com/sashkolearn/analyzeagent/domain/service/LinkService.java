package com.sashkolearn.analyzeagent.domain.service;

import com.sashkolearn.analyzeagent.domain.entity.Link;
import com.sashkolearn.analyzeagent.domain.entity.Note;
import com.sashkolearn.analyzeagent.domain.repository.LinkRepository;
import com.sashkolearn.analyzeagent.domain.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkService {

    private final LinkRepository linkRepository;
    private final NoteRepository noteRepository;
    private final WikilinkParserService wikilinkParser;

    /**
     * Builds links only for changed notes (new or updated)
     * More efficient than buildAllLinks() - doesn't rebuild unchanged notes
     */
    @Transactional
    public LinkBuildResult buildLinksForChangedNotes(List<UUID> changedNoteIds) {
        if (changedNoteIds.isEmpty()) {
            log.info("No changed notes to process for links");
            return new LinkBuildResult(0, 0, 0);
        }

        log.info("Building links for {} changed notes", changedNoteIds.size());

        int totalLinks = 0;
        int brokenLinks = 0;

        for (UUID noteId : changedNoteIds) {
            Optional<Note> noteOpt = noteRepository.findById(noteId);
            if (noteOpt.isEmpty()) {
                log.warn("Note not found: {}", noteId);
                continue;
            }

            try {
                LinkStats stats = buildLinksForNote(noteOpt.get());
                totalLinks += stats.created();
                brokenLinks += stats.broken();
            } catch (Exception e) {
                log.error("Failed to build links for note: {}", noteId, e);
            }
        }

        LinkBuildResult result = new LinkBuildResult(changedNoteIds.size(), totalLinks, brokenLinks);
        log.info("Link building for changed notes completed: {}", result);
        return result;
    }

    /**
     * Builds links for a single note
     */
    @Transactional
    public LinkStats buildLinksForNote(Note fromNote) {
        log.debug("Building links for note: {}", fromNote.getFileName());

        // Delete old links for this note
        linkRepository.deleteAllLinksForNote(fromNote.getId());

        List<String> wikilinks = wikilinkParser.extractWikilinks(fromNote.getContent());

        if (wikilinks.isEmpty()) {
            log.debug("No wikilinks found in: {}", fromNote.getFileName());
            return new LinkStats(0, 0);
        }

        int createdLinks = 0;
        int brokenLinks = 0;

        for (String wikilinkFileName : wikilinks) {

            String normalizedFileName = wikilinkParser.normalizeFileName(wikilinkFileName);

            Optional<Note> targetNote = noteRepository.findByFileName(normalizedFileName);

            if (targetNote.isEmpty()) {
                log.warn("Broken wikilink in {}: [[{}]] - target not found",
                        fromNote.getFileName(), wikilinkFileName);
                brokenLinks++;
                continue;
            }

            // Don't create self-references (application level protection)
            if (targetNote.get().getId().equals(fromNote.getId())) {
                log.debug("Skipping self-reference in: {}", fromNote.getFileName());
                continue;
            }

            Link link = Link.builder()
                    .fromId(fromNote.getId())
                    .toId(targetNote.get().getId())
                    .label("RELATED")
                    .build();

            try {
                linkRepository.save(link);
                createdLinks++;
                log.debug("Created link: {} -> {}", fromNote.getFileName(), targetNote.get().getFileName());

            } catch (Exception e) {
                log.debug("Duplicate link ignored: {} -> {}",
                        fromNote.getFileName(), targetNote.get().getFileName());
            }
        }

        log.info("Created {} links ({} broken) for: {}",
                createdLinks, brokenLinks, fromNote.getFileName());

        return new LinkStats(createdLinks, brokenLinks);
    }

    /**
     * Finds all related notes (outgoing + incoming)
     */
    public List<Note> findRelatedNotes(Note note) {
        List<Link> outgoingLinks = linkRepository.findByFromId(note.getId());
        List<Link> incomingLinks = linkRepository.findByToId(note.getId());

        return java.util.stream.Stream.concat(
                        outgoingLinks.stream().map(link -> noteRepository.findById(link.getToId()).orElse(null)),
                        incomingLinks.stream().map(link -> noteRepository.findById(link.getFromId()).orElse(null))
                )
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private record LinkStats(int created, int broken) {
    }

    public record LinkBuildResult(
            int totalNotes,
            int totalLinks,
            int brokenLinks
    ) {
    }
}
