package com.example.kaoyan.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识点掌握度实体
 */
@Data
@Entity
@Table(name = "knowledge_mastery", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "knowledge_point_id"})
})
public class KnowledgeMastery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "knowledge_point_id", nullable = false)
    private Long knowledgePointId;

    @Column(name = "correct_count")
    private Integer correctCount = 0;

    @Column(name = "total_count")
    private Integer totalCount = 0;

    @Column(name = "mastery_level", precision = 5, scale = 2)
    private BigDecimal masteryLevel = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_point_id", insertable = false, updatable = false)
    private KnowledgePoint knowledgePoint;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
