package com.sashkolearn.mainagent.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.File;

@Configuration
@Getter
public class FileStorageConfig {

    @Value("${file.storage.base-path}")
    private String basePath;

    @Value("${file.storage.max-file-size}")
    private long maxFileSize;

    @PostConstruct
    public void init() {
        File directory = new File(basePath);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }
}
