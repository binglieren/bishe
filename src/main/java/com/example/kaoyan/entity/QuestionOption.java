package com.example.kaoyan.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 题目选项实体（选择题专用）
 */
@Data
@Entity
@Table(name = "question_option")
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    /** 选项标签：A、B、C、D */
    @Column(nullable = false, length = 10)
    private String label;

    /** 选项内容 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_correct")
    private Boolean isCorrect = false;
}
