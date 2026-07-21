package com.sashkolearn.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "topic", nullable = false, columnDefinition = "TEXT")
    private String topic;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "embedding", columnDefinition = "vector(1536)", insertable = false, updatable = false)
    private String embeddingReadOnly;

    @Column(name = "created_by_chat_id", nullable = false)
    private Long createdByChatId;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean hasEmbedding() {
        return embeddingReadOnly != null && !embeddingReadOnly.isEmpty();
    }
}
