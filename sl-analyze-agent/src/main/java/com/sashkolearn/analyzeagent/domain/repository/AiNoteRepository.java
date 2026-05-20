package com.sashkolearn.analyzeagent.domain.repository;

import com.sashkolearn.analyzeagent.domain.entity.AiNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiNoteRepository extends JpaRepository<AiNote, UUID> {

    Optional<AiNote> findByFilePath(String filePath);

    @Modifying
    @Query(value = "UPDATE ai_notes SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id, @Param("embedding") String embedding);

    @Query(value = """
        SELECT * FROM ai_notes
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<AiNote> findSimilarNotes(@Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);
}
