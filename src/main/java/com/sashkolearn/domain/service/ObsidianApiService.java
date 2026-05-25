package com.sashkolearn.domain.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sashkolearn.config.NotesConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for the Obsidian Local REST API. The app runs on a remote server (Beelink) and
 * reaches the laptop's Obsidian over Tailscale, so this is the single channel for both
 * {@code /sync} (vault enumeration + read/write) and {@code /ai_analyze} (active note).
 */
@Service
@Slf4j
public class ObsidianApiService {

    private static final String ACTIVE_NOTE_ACCEPT = "application/vnd.olrapi.note+json";
    private static final MediaType MARKDOWN = MediaType.parseMediaType("text/markdown");

    private final String apiUrl;
    private final NotesConfig notesConfig;
    private final RestClient restClient;

    public ObsidianApiService(
            @Value("${obsidian.api.url:https://localhost:27124}") String apiUrl,
            @Value("${obsidian.api.token}") String apiToken,
            NotesConfig notesConfig
    ) {
        this.apiUrl = apiUrl;
        this.notesConfig = notesConfig;

        // Short connect timeout so the offline pre-check fails fast when the laptop is off;
        // generous read timeout because a sync reads many notes over Tailscale.
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(3))
                .withReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .build();
    }

    /**
     * @return true if the Obsidian REST API answered (even with an HTTP error); false on any
     * connection-level failure (host unreachable, connection refused, timeout) — i.e. laptop off.
     */
    public boolean isReachable() {
        try {
            restClient.get().uri("/").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            return true; // server responded with an HTTP status — it is up
        } catch (Exception e) {
            log.info("Obsidian REST API not reachable at {}: {}", apiUrl, e.getMessage());
            return false;
        }
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

    /** Recursively enumerates every {@code .md} file under the configured notes folder, returning vault-relative paths. */
    public List<String> listMarkdownFiles() {
        List<String> result = new ArrayList<>();
        collectMarkdown(notesConfig.getNotesFolder(), result);
        log.info("Listed {} markdown files from Obsidian vault folder '{}'", result.size(), notesConfig.getNotesFolder());
        return result;
    }

    private void collectMarkdown(String folder, List<String> out) {
        FilesResponse response = restClient.get()
                .uri(vaultUri(folder, true))
                .retrieve()
                .body(FilesResponse.class);

        if (response == null || response.files() == null) {
            return;
        }

        for (String entry : response.files()) {
            String childPath = folder.isBlank() ? entry : folder + "/" + entry;
            if (entry.endsWith("/")) {
                collectMarkdown(childPath.substring(0, childPath.length() - 1), out);
            } else if (entry.toLowerCase().endsWith(".md")) {
                out.add(childPath);
            }
        }
    }

    /** Reads the raw markdown content of a vault-relative path (e.g. {@code topics/foo.md}). */
    public String readNote(String vaultPath) {
        return restClient.get()
                .uri(vaultUri(vaultPath, false))
                .accept(MediaType.TEXT_MARKDOWN, MediaType.TEXT_PLAIN, MediaType.ALL)
                .retrieve()
                .body(String.class);
    }

    /** Reads a binary file (e.g. an image attachment) by its vault-relative path. */
    public byte[] readBinary(String vaultPath) {
        return restClient.get()
                .uri(vaultUri(vaultPath, false))
                .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL)
                .retrieve()
                .body(byte[].class);
    }

    /** Overwrites a vault file with the given markdown content (used to write image descriptions back). */
    public void writeNote(String vaultPath, String content) {
        restClient.put()
                .uri(vaultUri(vaultPath, false))
                .contentType(MARKDOWN)
                .body(content)
                .retrieve()
                .toBodilessEntity();
    }

    /** Builds an encoded {@code /vault/...} URI, preserving path separators and adding a trailing slash for directories. */
    private URI vaultUri(String vaultPath, boolean directory) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(apiUrl).pathSegment("vault");
        if (vaultPath != null && !vaultPath.isBlank()) {
            for (String segment : vaultPath.split("/")) {
                if (!segment.isEmpty()) {
                    builder.pathSegment(segment);
                }
            }
        }
        if (directory) {
            builder.path("/");
        }
        return builder.encode().build().toUri();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObsidianNoteResponse(
            String content,
            String path
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FilesResponse(
            List<String> files
    ) {
    }

    public record ActiveNote(
            String fileName,
            String filePath,
            String content
    ) {
    }
}
