package com.sashkolearn.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Vault layout for the Obsidian REST API sync. Notes and images are addressed by
 * vault-relative paths (e.g. {@code topics/foo.md}, {@code img/bar.png}) — the app no
 * longer reads them from the local filesystem.
 */
@Configuration
@ConfigurationProperties(prefix = "obsidian")
@Data
public class NotesConfig {

    /** Vault-relative folder that holds the markdown notes (e.g. {@code topics}). */
    private String notesFolder;

    /** Vault-relative folder that holds image attachments (e.g. {@code img}). */
    private String imgFolder;

    private SyncConfig sync = new SyncConfig();

    @Data
    public static class SyncConfig {
        private int batchSize = 10;
    }
}
