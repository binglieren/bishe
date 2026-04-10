package com.example.kaoyan.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档实体
 */
@Data
@Entity
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_type", length = 50)
    private String fileType;

    /** 状态：PROCESSING, COMPLETED, FAILED */
    @Column(length = 20)
    private String status = "PROCESSING";

    @Column(name = "upload_time")
    private LocalDateTime uploadTime = LocalDateTime.now();
}
