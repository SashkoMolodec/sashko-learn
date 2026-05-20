package com.sashkolearn.domain.service;

import com.sashkolearn.domain.model.ChapterInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterExtractionService {

    private final PdfProcessingService pdfProcessingService;

    public List<ChapterInfo> extractChapters(String filePath) throws Exception {
        log.info("Extracting chapters from {}", filePath);
        List<ChapterInfo> chapters = pdfProcessingService.extractChapterTitles(filePath);
        log.info("Extracted {} chapters from {}", chapters.size(), filePath);
        return chapters;
    }
}
