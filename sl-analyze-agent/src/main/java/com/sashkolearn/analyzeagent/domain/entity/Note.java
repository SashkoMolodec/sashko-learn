package com.sashkolearn.analyzeagent.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 1000, unique = true)
    private String filePath;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    // Store as String, PostgreSQL will cast to vector
    @Column(name = "embedding", columnDefinition = "vector(1536)", insertable = false, updatable = false)
    private String embeddingReadOnly;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public float[] getEmbeddingAsFloats() {
        if (embeddingReadOnly == null) return null;
        // Parse "[0.1,0.2,...]" format
        String clean = embeddingReadOnly.replaceAll("[\\[\\]]", "");
        if (clean.isEmpty()) return new float[0];
        String[] parts = clean.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    public boolean hasEmbedding() {
        return embeddingReadOnly != null && !embeddingReadOnly.isEmpty();
    }
}
