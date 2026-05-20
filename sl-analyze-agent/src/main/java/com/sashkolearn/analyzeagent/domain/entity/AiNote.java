package com.sashkolearn.analyzeagent.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 1000, unique = true)
    private String filePath;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_url", length = 2000)
    private String sourceUrl;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "embedding", columnDefinition = "vector(1536)", insertable = false, updatable = false)
    private String embeddingReadOnly;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
