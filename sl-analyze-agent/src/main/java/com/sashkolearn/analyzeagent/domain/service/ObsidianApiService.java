package com.sashkolearn.analyzeagent.domain.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;

@Service
@Slf4j
public class ObsidianApiService {

    private static final String ACTIVE_NOTE_ACCEPT = "application/vnd.olrapi.note+json";

    private final RestClient restClient;

    public ObsidianApiService(
            @Value("${obsidian.api.url:https://localhost:27124}") String apiUrl,
            @Value("${obsidian.api.token}") String apiToken
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .build();
    }

    public ActiveNote getActiveNote() {
        log.info("Fetching active note from Obsidian");

        ObsidianNoteResponse response = restClient.get()
                .uri("/active/")
                .accept(MediaType.parseMediaType(ACTIVE_NOTE_ACCEPT))
                .retrieve()
                .body(ObsidianNoteResponse.class);

        if (response == null || response.content() == null) {
            throw new RuntimeException("No active note found in Obsidian");
        }

        String filePath = response.path();
        String fileName = Path.of(filePath).getFileName().toString();

        log.info("Active note: {} ({})", fileName, filePath);
        return new ActiveNote(fileName, filePath, response.content());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObsidianNoteResponse(
            String content,
            String path
    ) {
    }

    public record ActiveNote(
            String fileName,
            String filePath,
            String content
    ) {
    }
}
