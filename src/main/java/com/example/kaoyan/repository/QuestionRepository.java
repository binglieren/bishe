package com.example.kaoyan.repository;

import com.example.kaoyan.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    Page<Question> findBySubject(String subject, Pageable pageable);

    Page<Question> findBySubjectAndType(String subject, String type, Pageable pageable);

    Page<Question> findBySubjectAndDifficulty(String subject, Integer difficulty, Pageable pageable);

    Page<Question> findByKnowledgePointId(Long knowledgePointId, Pageable pageable);

    @Query("SELECT q FROM Question q WHERE q.subject = :subject AND q.year = :year")
    Page<Question> findBySubjectAndYear(String subject, Integer year, Pageable pageable);

    @Query("SELECT q FROM Question q WHERE q.subject = :subject ORDER BY RANDOM()")
    List<Question> findRandomBySubject(String subject, Pageable pageable);

    @Query("SELECT q FROM Question q WHERE q.knowledgePointId = :knowledgePointId ORDER BY RANDOM()")
    List<Question> findRandomByKnowledgePointId(Long knowledgePointId, Pageable pageable);
}
