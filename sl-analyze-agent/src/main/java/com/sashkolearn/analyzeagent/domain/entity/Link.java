package com.sashkolearn.analyzeagent.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(Link.LinkId.class)
public class Link {

    @Id
    @Column(name = "from_id")
    private UUID fromId;

    @Id
    @Column(name = "to_id")
    private UUID toId;

    @Id
    @Column(name = "label", length = 50)
    private String label;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkId implements Serializable {
        private UUID fromId;
        private UUID toId;
        private String label;
    }
}
