package com.example.kaoyan.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话会话列表项 DTO
 * 用于 AI 问答一级页面（会话列表）
 */
@Data
public class ChatSessionDTO {
    private Long id;
    private String title;
    private String lastMessagePreview;
    private String lastMessageRole;
    private Integer messageCount;
    private Long knowledgeBaseId;          // 兼容旧单知识库字段
    private List<Long> knowledgeBaseIds;   // 多知识库绑定列表（新）
    private Boolean thinkingEnabled;
    private String systemPrompt;           // 会话级自定义 Prompt
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
