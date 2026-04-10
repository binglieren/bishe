package com.example.kaoyan.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目实体
 */
@Data
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 科目：政治、英语、数学、专业课 */
    @Column(nullable = false, length = 20)
    private String subject;

    /** 题型：单选、多选、填空、简答、证明 */
    @Column(nullable = false, length = 20)
    private String type;

    /** 难度 1-5 */
    @Column(nullable = false)
    private Integer difficulty;

    /** 题目内容 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 参考答案 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    /** 解析 */
    @Column(columnDefinition = "TEXT")
    private String analysis;

    @Column(name = "knowledge_point_id")
    private Long knowledgePointId;

    /** 年份（真题年份） */
    private Integer year;

    /** 来源 */
    @Column(length = 100)
    private String source;

    @OneToMany(mappedBy = "questionId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<QuestionOption> options;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
