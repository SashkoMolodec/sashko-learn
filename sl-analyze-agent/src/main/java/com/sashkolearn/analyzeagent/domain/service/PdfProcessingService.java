package com.sashkolearn.analyzeagent.domain.service;

import com.sashkolearn.analyzeagent.messaging.producer.dto.ExtractChaptersResultDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PdfProcessingService {

    public List<ExtractChaptersResultDto.ChapterInfo> extractChapterTitles(String pdfPath) throws IOException {
        List<ExtractChaptersResultDto.ChapterInfo> chapters = new ArrayList<>();
        return chapters;
       /* try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            log.info("Processing PDF: {}, pages: {}", pdfPath, document.getNumberOfPages());

            // Strategy 1: Try to extract from PDF bookmarks/outline
            chapters = extractFromBookmarks(document);

            if (!chapters.isEmpty()) {
                log.info("Extracted {} chapters from PDF bookmarks", chapters.size());
                return chapters;
            }

            // Strategy 2: Fall back to text analysis
            chapters = extractFromTextAnalysis(document);

            if (!chapters.isEmpty()) {
                log.info("Extracted {} chapters from text analysis", chapters.size());
                return chapters;
            }

            log.warn("No chapters found in PDF: {}", pdfPath);
            // Return at least something - create a single chapter for the whole book
            return List.of(new ExtractChaptersResultDto.ChapterInfo(
                1,
                "Complete Book",
                1,
                document.getNumberOfPages()
            ));
        }*/
    }

   /* private List<ExtractChaptersResultDto.ChapterInfo> extractFromBookmarks(PDDocument document) {
        List<ExtractChaptersResultDto.ChapterInfo> chapters = new ArrayList<>();

        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline == null) {
            log.debug("No PDF outline found");
            return chapters;
        }

        int chapterNum = 1;
        PDOutlineNode current = outline.getFirstChild();

        while (current != null) {
            if (current instanceof PDOutlineItem item) {
                String title = item.getTitle();

                // Filter out non-chapter items (e.g., "Preface", "Index")
                if (isChapterTitle(title)) {
                    chapters.add(new ExtractChaptersResultDto.ChapterInfo(
                        chapterNum++,
                        title,
                        null,  // Page numbers would require additional complex logic
                        null
                    ));
                    log.debug("Found chapter from bookmark: {}", title);
                }
            }
            current = current.getNextSibling();
        }

        return chapters;
    }*/

    private List<ExtractChaptersResultDto.ChapterInfo> extractFromTextAnalysis(PDDocument document) throws IOException {
        List<ExtractChaptersResultDto.ChapterInfo> chapters = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper();

        // Analyze first 50 pages for chapter patterns
        int pagesToAnalyze = Math.min(100, document.getNumberOfPages());

        for (int i = 1; i <= pagesToAnalyze; i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            String pageText = stripper.getText(document);

            // Look for chapter patterns
            chapters.addAll(findChaptersInText(pageText, i));
        }

        return chapters;
    }

    private List<ExtractChaptersResultDto.ChapterInfo> findChaptersInText(String text, int pageNumber) {
        List<ExtractChaptersResultDto.ChapterInfo> chapters = new ArrayList<>();

        // Pattern 1: "Chapter 1: Title" or "Chapter 1. Title"
        Pattern pattern1 = Pattern.compile(
            "^(Chapter|CHAPTER)\\s+(\\d+)[:.\\s]+(.*?)$",
            Pattern.MULTILINE
        );

        // Pattern 2: "1. Title" (as first significant line)
        Pattern pattern2 = Pattern.compile(
            "^(\\d+)\\.\\s+([A-Z][\\w\\s]+)$",
            Pattern.MULTILINE
        );

        // Pattern 3: "CHAPTER 1\nTitle" (chapter number and title on separate lines)
        Pattern pattern3 = Pattern.compile(
            "^(CHAPTER|Chapter)\\s+(\\d+)\\s*$\\n^([A-Z][\\w\\s]+)$",
            Pattern.MULTILINE
        );

        // Try Pattern 1
        Matcher matcher1 = pattern1.matcher(text);
        while (matcher1.find()) {
            int chapterNum = Integer.parseInt(matcher1.group(2));
            String title = matcher1.group(3).trim();

            if (!title.isEmpty() && title.length() < 200) {
                chapters.add(new ExtractChaptersResultDto.ChapterInfo(
                    chapterNum,
                    cleanTitle(title),
                    pageNumber,
                    null
                ));
                log.debug("Found chapter (pattern 1): Chapter {}: {}", chapterNum, title);
            }
        }

        // Try Pattern 3 if no chapters found yet
        if (chapters.isEmpty()) {
            Matcher matcher3 = pattern3.matcher(text);
            while (matcher3.find()) {
                int chapterNum = Integer.parseInt(matcher3.group(2));
                String title = matcher3.group(3).trim();

                if (title.length() < 200) {
                    chapters.add(new ExtractChaptersResultDto.ChapterInfo(
                        chapterNum,
                        cleanTitle(title),
                        pageNumber,
                        null
                    ));
                    log.debug("Found chapter (pattern 3): Chapter {}: {}", chapterNum, title);
                }
            }
        }

        return chapters;
    }

    private boolean isChapterTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }

        String lower = title.toLowerCase().trim();

        // Exclude common non-chapter sections
        if (lower.matches("^(preface|foreword|introduction|appendix|index|bibliography|references|glossary|contents|table of contents)$")) {
            return false;
        }

        // Include if starts with "Chapter" or contains digit
        return lower.matches("^chapter.*") || lower.matches(".*\\d+.*");
    }

    private String cleanTitle(String title) {
        // Remove extra whitespace
        title = title.replaceAll("\\s+", " ").trim();

        // Remove trailing dots and dashes
        title = title.replaceAll("[.\\-]+$", "");

        return title;
    }
}
