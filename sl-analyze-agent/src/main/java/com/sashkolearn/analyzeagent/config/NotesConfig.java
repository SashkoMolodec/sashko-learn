package com.sashkolearn.analyzeagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "notes")
@Data
public class NotesConfig {

    private String path;
    private SyncConfig sync = new SyncConfig();

    @Data
    public static class SyncConfig {
        private int batchSize = 10;
    }
}
