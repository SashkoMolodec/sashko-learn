package com.sashkolearn.domain.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads prompt templates from classpath:prompts/.
 * Caches loaded files in memory — prompts don't change at runtime, only at restart.
 * Supports simple {placeholder} substitution.
 */
@Component
@Slf4j
public class PromptLoader {

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new HashMap<>();

    public PromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Load a prompt file as-is. Path is relative to classpath:prompts/.
     * Example: load("quiz/system-generator.md")
     */
    public String load(String pathUnderPrompts) {
        return cache.computeIfAbsent(pathUnderPrompts, this::readFromClasspath);
    }

    /**
     * Load and substitute {placeholder} tokens.
     */
    public String render(String pathUnderPrompts, Map<String, String> vars) {
        String template = load(pathUnderPrompts);
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    private String readFromClasspath(String path) {
        Resource resource = resourceLoader.getResource("classpath:prompts/" + path);
        try (var in = resource.getInputStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            log.info("Loaded prompt: {} ({} chars)", path, content.length());
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt: " + path, e);
        }
    }
}
