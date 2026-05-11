package com.example.kaoyan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentChunk {

    private Long id;
    private Long documentId;
    private String content;
    private Integer chunkIndex;
    private String embedding;
    private LocalDateTime createdAt;
}
