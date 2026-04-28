package com.example.kaoyan.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 用户题库实体：记录用户通过拍照问答积累的题目及做题统计
 */
@Data
@Entity
@Table(name = "user_question")
public class UserQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    /** 来源会话ID（该题目是从哪次聊天中提取的） */
    @Column(name = "source_session_id")
    private Long sourceSessionId;

    /** 上次做题时间 */
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    /** 累计答对次数 */
    @Column(name = "correct_count")
    private Integer correctCount = 0;

    /** 累计做题次数 */
    @Column(name = "total_attempts")
    private Integer totalAttempts = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** 关联题目（含选项，eagerly fetched 以便 JSON 序列化） */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private Question question;
}
