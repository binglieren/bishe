package com.example.kaoyan.dto;

import lombok.Data;

@Data
public class AiConfigDTO {
    private String apiKey;
    private String apiUrl;
    private String chatModel;
    private String embeddingModel;
    private Double temperature;
    private Integer maxTokens;
    private String systemPrompt;
}