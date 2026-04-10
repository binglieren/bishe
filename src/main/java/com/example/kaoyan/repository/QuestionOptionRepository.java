package com.example.kaoyan.repository;

import com.example.kaoyan.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {

    List<QuestionOption> findByQuestionId(Long questionId);

    void deleteByQuestionId(Long questionId);
}
