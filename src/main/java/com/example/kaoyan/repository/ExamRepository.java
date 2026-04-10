package com.example.kaoyan.repository;

import com.example.kaoyan.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findBySubject(String subject);
}
