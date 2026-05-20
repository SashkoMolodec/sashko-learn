package com.sashkolearn.domain.repository;

import com.sashkolearn.domain.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    @Query(value = """
        SELECT * FROM quizzes
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Quiz> findSimilarQuizzes(@Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);

    @Modifying
    @Query(value = "UPDATE quizzes SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id, @Param("embedding") String embedding);

    List<Quiz> findByCreatedByChatId(Long chatId);

    Optional<Quiz> findByTopicIgnoreCase(String topic);
}
