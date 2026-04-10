package com.example.kaoyan.repository;

import com.example.kaoyan.entity.ExamAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExamAnswerRepository extends JpaRepository<ExamAnswer, Long> {

    List<ExamAnswer> findByRecordId(Long recordId);

    @Query("SELECT COALESCE(SUM(ea.score), 0) FROM ExamAnswer ea WHERE ea.recordId = :recordId")
    Integer sumScoreByRecordId(Long recordId);
}
