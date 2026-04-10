package com.example.kaoyan.repository;

import com.example.kaoyan.entity.ExamRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamRecordRepository extends JpaRepository<ExamRecord, Long> {

    List<ExamRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ExamRecord> findByUserIdAndStatus(Long userId, String status);

    Optional<ExamRecord> findByUserIdAndExamIdAndStatus(Long userId, Long examId, String status);
}
