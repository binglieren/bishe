package com.example.kaoyan.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 对话会话实体
 */
@Data
@Entity
@Table(name = "chat_session")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 200)
    private String title;

    /** 本次会话绑定的知识库，null = 不启用 RAG 检索 */
    @Column(name = "knowledge_base_id")
    private Long knowledgeBaseId;

    /** 深度思考模式：下一条消息是否启用 thinking */
    @Column(name = "thinking_enabled")
    private Boolean thinkingEnabled;

    /** 会话级自定义系统 Prompt（为 null 时回退到 AiConfig / SystemApiConfig / 默认值） */
    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    /** 会话上下文（AI 提取的关键信息 JSON：subject, topic, level, focusPoints） */
    @Column(name = "session_context", columnDefinition = "TEXT")
    private String sessionContext;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
