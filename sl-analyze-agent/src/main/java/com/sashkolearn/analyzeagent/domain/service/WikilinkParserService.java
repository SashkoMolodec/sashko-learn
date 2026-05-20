package com.sashkolearn.analyzeagent.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class WikilinkParserService {

    // Matches [[link]] but not ![[image]]
    private static final Pattern WIKILINK_PATTERN = Pattern.compile("(?<!!)\\[\\[([^\\]]+)\\]\\]");

    /**
     * Extracts wikilinks from markdown content
     *
     * Examples:
     * - [[Note]] -> "Note"
     * - [[Note|alias]] -> "Note" (ignores alias)
     * - ![[image.png]] -> ignored (embedded image)
     *
     * @param content markdown text
     * @return list of normalized file names
     */
    public List<String> extractWikilinks(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> wikilinks = new ArrayList<>();
        Matcher matcher = WIKILINK_PATTERN.matcher(content);

        while (matcher.find()) {
            String link = matcher.group(1).trim();

            // Handle [[note|alias]] format - take only the note part
            if (link.contains("|")) {
                link = link.substring(0, link.indexOf("|")).trim();
            }

            if (link.isEmpty()) {
                continue;
            }

            String normalizedFileName = normalizeFileName(link);
            wikilinks.add(normalizedFileName);
        }

        log.debug("Extracted {} wikilinks from content", wikilinks.size());
        return wikilinks;
    }

    /**
     * Normalizes a file name for wikilink matching
     *
     * - Takes only the filename (removes path if present)
     * - Adds .md extension if missing
     * - Handles both / and \ path separators
     *
     * Examples:
     * - "Note" -> "Note.md"
     * - "Note.md" -> "Note.md"
     * - "folder/Note" -> "Note.md"
     *
     * @param fileName raw file name from wikilink
     * @return normalized file name
     */
    public String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }

        // Take only the filename (remove path)
        String name = fileName;
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        // Add .md extension if missing
        if (!name.toLowerCase().endsWith(".md")) {
            name = name + ".md";
        }

        return name;
    }
}
