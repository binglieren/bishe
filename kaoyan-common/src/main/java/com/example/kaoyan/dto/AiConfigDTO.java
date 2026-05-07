package com.example.kaoyan.dto;

import lombok.Data;

@Data
public class AiConfigDTO {
    private String apiKey;
    private String apiUrl;
    private String chatModel;
    private String embeddingModel;
    /** Embedding 独立端点（留空则使用 apiUrl/apiKey） */
    private String embeddingApiUrl;
    private String embeddingApiKey;
    private Double temperature;
    private Integer maxTokens;
    private String systemPrompt;
}