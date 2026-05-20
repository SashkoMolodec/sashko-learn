package com.sashkolearn.analyzeagent.domain.repository;

import com.sashkolearn.analyzeagent.domain.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, String> {

    boolean existsByFileName(String fileName);

    List<Attachment> findByNoteId(UUID noteId);

    @Modifying
    @Query(value = "UPDATE attachments SET embedding = CAST(:embedding AS vector) WHERE file_name = :fileName", nativeQuery = true)
    void updateEmbedding(@Param("fileName") String fileName, @Param("embedding") String embedding);

    @Modifying
    @Query(value = "DELETE FROM attachments WHERE note_id = :noteId", nativeQuery = true)
    void deleteByNoteId(@Param("noteId") UUID noteId);

    @Query(value = """
        SELECT * FROM attachments
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Attachment> findSimilarAttachments(@Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);
}
