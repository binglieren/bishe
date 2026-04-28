package com.example.kaoyan.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 对话消息实体
 */
@Data
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 角色：user / assistant / system */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 图片 base64 编码（拍照搜题时存储） */
    @Column(name = "image_base64", columnDefinition = "TEXT")
    private String imageBase64;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
