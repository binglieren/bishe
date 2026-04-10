package com.example.kaoyan.repository;

import com.example.kaoyan.entity.WrongAnswerRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WrongAnswerRepository extends JpaRepository<WrongAnswerRecord, Long> {

    Page<WrongAnswerRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<WrongAnswerRecord> findByUserIdAndIsResolvedOrderByCreatedAtDesc(Long userId, Boolean isResolved, Pageable pageable);

    boolean existsByUserIdAndQuestionId(Long userId, Long questionId);
}
