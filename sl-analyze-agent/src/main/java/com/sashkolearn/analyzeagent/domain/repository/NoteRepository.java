package com.sashkolearn.analyzeagent.domain.repository;

import com.sashkolearn.analyzeagent.domain.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoteRepository extends JpaRepository<Note, UUID> {

    Optional<Note> findByFilePath(String filePath);

    Optional<Note> findByFileName(String fileName);

    boolean existsByFilePath(String filePath);

    @Query(value = "SELECT * FROM notes WHERE embedding IS NULL AND content IS NOT NULL AND LENGTH(TRIM(content)) > 0", nativeQuery = true)
    List<Note> findNotesWithoutEmbedding();

    @Modifying
    @Query(value = "UPDATE notes SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id, @Param("embedding") String embedding);

    @Modifying
    @Query(value = "UPDATE notes SET embedding = NULL WHERE id = :id", nativeQuery = true)
    void clearEmbedding(@Param("id") UUID id);

    @Query(value = """
        SELECT * FROM notes
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Note> findSimilarNotes(@Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);
}
