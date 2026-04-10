package com.example.kaoyan.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 文档分块实体（含向量嵌入）
 */
@Data
@Entity
@Table(name = "document_chunk")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** 分块文本内容 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    /**
     * 向量嵌入存储为 float 数组，
     * 通过原生 SQL 与 pgvector 交互
     */
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private String embedding;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
