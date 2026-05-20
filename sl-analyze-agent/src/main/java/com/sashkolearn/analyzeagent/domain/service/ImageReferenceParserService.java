package com.sashkolearn.analyzeagent.domain.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImageReferenceParserService {

    // Pattern for Obsidian image references: ![[filename.png]] or ![[filename.png|width]]
    private static final Pattern IMAGE_REFERENCE_PATTERN = Pattern.compile("!\\[\\[([^\\]|]+)(?:\\|[^\\]]*)?\\]\\]");

    private static final List<String> IMAGE_EXTENSIONS = List.of(
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".svg"
    );

    public List<String> extractImageReferences(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }

        List<String> imageRefs = new ArrayList<>();
        Matcher matcher = IMAGE_REFERENCE_PATTERN.matcher(content);

        while (matcher.find()) {
            String fileName = matcher.group(1).trim();
            if (isImageFile(fileName)) {
                imageRefs.add(fileName);
            }
        }

        return imageRefs;
    }

    private boolean isImageFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }
}
