package com.example.kaoyan.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 全局 API 配置（管理员维护，按"环节"存储）
 *
 * stage 枚举：
 *   - chat       文本对话（AI 答疑文本路径、标题生成、题目打标）
 *   - multimodal 多模态对话（拍照搜题、题目图片结构化）
 *   - embedding  向量化（RAG 检索、文档切片、题目向量化）
 *   - audio      语音识别（Whisper STT）
 *
 * 解析链：用户 AiConfig → 本表 → application.yml 默认值
 */
@Data
@Entity
@Table(name = "system_api_config")
public class SystemApiConfig {

    /** 环节标识，主键 */
    @Id
    @Column(length = 32)
    private String stage;

    /** API base URL（不含 /chat/completions 等后缀） */
    @Column(name = "api_url", length = 500)
    private String apiUrl;

    /** API Key，仅服务端读写，不出现在任何 GET 响应中 */
    @Column(name = "api_key", length = 500)
    private String apiKey;

    /** 该环节使用的模型名 */
    @Column(length = 100)
    private String model;

    /** 仅 chat 环节有效：采样温度 */
    @Column
    private Double temperature;

    /** 仅 chat 环节有效：最大输出 tokens */
    @Column(name = "max_tokens")
    private Integer maxTokens;

    /** 仅 chat 环节有效：系统提示词 */
    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    /** 是否启用；false 时跳过该环节配置直接走 yml 默认 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 备注说明，便于管理员区分多套配置 */
    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
