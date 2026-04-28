package com.example.kaoyan.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 题目-知识点多对多关联。
 *
 * 一道题可挂多个知识点，每个挂点带权重（LLM 置信度）和来源。
 * 考研综合题依赖这张表来表达"一题多考点"。
 */
@Data
@Entity
@Table(name = "question_knowledge_point")
@IdClass(QuestionKnowledgePoint.PK.class)
public class QuestionKnowledgePoint {

    @Id
    @Column(name = "question_id")
    private Long questionId;

    @Id
    @Column(name = "knowledge_point_id")
    private Long knowledgePointId;

    /** 置信度/权重 0.0 ~ 1.0 */
    @Column(precision = 3, scale = 2)
    private BigDecimal weight = new BigDecimal("1.00");

    /** 来源：llm | manual | extracted */
    @Column(length = 20)
    private String source = "llm";

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "knowledge_point_id", insertable = false, updatable = false)
    private KnowledgePoint knowledgePoint;

    @Data
    public static class PK implements Serializable {
        private Long questionId;
        private Long knowledgePointId;
    }
}
