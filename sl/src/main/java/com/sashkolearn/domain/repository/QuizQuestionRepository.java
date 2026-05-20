package com.sashkolearn.domain.repository;

import com.sashkolearn.domain.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findByQuizIdOrderByQuestionNumber(UUID quizId);

    Optional<QuizQuestion> findByQuizIdAndQuestionNumber(UUID quizId, Integer questionNumber);

    int countByQuizId(UUID quizId);
}
