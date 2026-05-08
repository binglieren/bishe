package com.example.kaoyan.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonIgnore
    @Column(name = "image_base64", columnDefinition = "TEXT")
    private String imageBase64;

    @Column(name = "reasoning_content", columnDefinition = "TEXT")
    private String reasoningContent;

    @Column(name = "content_html", columnDefinition = "TEXT")
    private String contentHtml;

    /**
     * 渲染元数据 JSON（segments 切段结构，含 kind/text/html）。
     * 不存高度（高度强依赖设备宽度，跨设备不可重用）。
     */
    @Column(name = "render_meta", columnDefinition = "TEXT")
    private String renderMeta;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
