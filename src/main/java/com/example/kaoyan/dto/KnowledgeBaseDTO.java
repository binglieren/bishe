package com.example.kaoyan.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库列表返回 DTO（含文档统计）
 */
@Data
public class KnowledgeBaseDTO {
    private Long id;
    private String name;
    private String description;
    private Long documentCount;
    private Long enabledCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
