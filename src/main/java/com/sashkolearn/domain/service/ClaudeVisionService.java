package com.sashkolearn.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClaudeVisionService {

    // Anthropic API base64 image limit is 5 MB; stay well under to avoid Jackson string-length errors too
    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private final AnthropicChatModel anthropicChatModel;

    private static final String IMAGE_DESCRIPTION_PROMPT = """
            This image comes from a notebook with the following content:
            ---
            %s
            ---

            Describe what you see in this image in detail, keeping in mind the notebook context above. Focus on:
            - Any text, labels, or written content
            - Diagrams, charts, or visual structures
            - Key visual elements and their relationships
            - How the image relates to the notebook's topic and content

            Provide a concise but comprehensive description that would help someone understand the image content without seeing it.
            """;

    /**
     * Analyzes an image using Claude Vision and returns a text description.
     *
     * @param imagePath path to the image file
     * @return text description of the image
     */
    public String describeImage(Path imagePath, String noteContent) {
        log.debug("Describing image: {}", imagePath);

        try {
            long fileSize = Files.size(imagePath);
            if (fileSize > MAX_IMAGE_BYTES) {
                log.warn("Skipping image {} ({} bytes) — exceeds {} byte limit for vision API",
                        imagePath.getFileName(), fileSize, MAX_IMAGE_BYTES);
                return null;
            }

            var imageResource = new FileSystemResource(imagePath);
            var mimeType = getMimeType(imagePath);
            var media = new Media(mimeType, imageResource);

            var promptText = String.format(IMAGE_DESCRIPTION_PROMPT, noteContent);
            var userMessage = UserMessage.builder().text(promptText).media(media).build();
            var prompt = new Prompt(userMessage);

            var response = anthropicChatModel.call(prompt);
            String description = response.getResult().getOutput().getText();

            log.debug("Generated description for {}: {} chars", imagePath.getFileName(), description.length());
            return description;

        } catch (Exception e) {
            log.error("Failed to describe image {}: {}", imagePath, e.getMessage());
            throw new RuntimeException("Failed to describe image: " + e.getMessage(), e);
        }
    }

    /**
     * Analyzes an in-memory image (fetched over the Obsidian REST API) using Claude Vision.
     *
     * @param imageBytes  raw image bytes
     * @param fileName    original file name (used only to infer the MIME type)
     * @param noteContent surrounding note text for context
     * @return text description, or {@code null} if the image exceeds the vision API size limit
     */
    public String describeImage(byte[] imageBytes, String fileName, String noteContent) {
        log.debug("Describing image: {}", fileName);

        try {
            if (imageBytes.length > MAX_IMAGE_BYTES) {
                log.warn("Skipping image {} ({} bytes) — exceeds {} byte limit for vision API",
                        fileName, imageBytes.length, MAX_IMAGE_BYTES);
                return null;
            }

            var imageResource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            var media = new Media(getMimeType(fileName), imageResource);

            var promptText = String.format(IMAGE_DESCRIPTION_PROMPT, noteContent);
            var userMessage = UserMessage.builder().text(promptText).media(media).build();
            var prompt = new Prompt(userMessage);

            var response = anthropicChatModel.call(prompt);
            String description = response.getResult().getOutput().getText();

            log.debug("Generated description for {}: {} chars", fileName, description.length());
            return description;

        } catch (Exception e) {
            log.error("Failed to describe image {}: {}", fileName, e.getMessage());
            throw new RuntimeException("Failed to describe image: " + e.getMessage(), e);
        }
    }

    private org.springframework.util.MimeType getMimeType(Path imagePath) {
        return getMimeType(imagePath.getFileName().toString());
    }

    private org.springframework.util.MimeType getMimeType(String fileNameRaw) {
        String fileName = fileNameRaw.toLowerCase();
        if (fileName.endsWith(".png")) {
            return MimeTypeUtils.IMAGE_PNG;
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return MimeTypeUtils.IMAGE_JPEG;
        } else if (fileName.endsWith(".gif")) {
            return MimeTypeUtils.IMAGE_GIF;
        } else if (fileName.endsWith(".webp")) {
            return MimeTypeUtils.parseMimeType("image/webp");
        } else {
            return MimeTypeUtils.APPLICATION_OCTET_STREAM;
        }
    }
}
