package com.example.kaoyan.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话列表项 DTO
 * 用于 AI 问答一级页面（会话列表）
 */
@Data
public class ChatSessionDTO {
    private Long id;
    private String title;              // LLM 生成的简称
    private String lastMessagePreview; // 最后一条消息预览（截断）
    private String lastMessageRole;    // user / assistant
    private Integer messageCount;
    private Long knowledgeBaseId;      // 绑定的知识库 id（null=未绑定）
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
