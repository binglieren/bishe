package com.example.kaoyan.controller;

import com.example.kaoyan.dto.AiConfigDTO;
import com.example.kaoyan.entity.AiConfig;
import com.example.kaoyan.repository.AiConfigRepository;
import com.example.kaoyan.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai-config")
@RequiredArgsConstructor
@Tag(name = "AI 配置", description = "用户自主配置 AI 模型参数")
public class AiConfigController {

    private final AiConfigRepository aiConfigRepository;

    @Value("${llm.api-key}")
    private String defaultApiKey;

    @Value("${llm.api-url}")
    private String defaultApiUrl;

    @Value("${llm.model}")
    private String defaultModel;

    @Value("${llm.embedding-model:}")
    private String defaultEmbeddingModel;

    @GetMapping
    @Operation(summary = "获取当前 AI 配置")
    public Result<AiConfigDTO> getConfig(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        AiConfig config = aiConfigRepository.findByUserId(userId).orElse(null);

        AiConfigDTO dto = new AiConfigDTO();
        if (config != null) {
            dto.setApiKey(config.getApiKey() != null ? "******" : null);
            dto.setApiUrl(config.getApiUrl());
            dto.setChatModel(config.getChatModel());
            dto.setEmbeddingModel(config.getEmbeddingModel());
            dto.setTemperature(config.getTemperature());
            dto.setMaxTokens(config.getMaxTokens());
            dto.setSystemPrompt(config.getSystemPrompt());
        } else {
            dto.setApiUrl(defaultApiUrl);
            dto.setChatModel(defaultModel);
            dto.setEmbeddingModel(defaultEmbeddingModel);
            dto.setTemperature(0.7);
            dto.setMaxTokens(2000);
        }
        return Result.success(dto);
    }

    @PostMapping
    @Operation(summary = "保存 AI 配置")
    public Result<AiConfigDTO> saveConfig(Authentication auth, @RequestBody AiConfigDTO dto) {
        Long userId = (Long) auth.getPrincipal();
        AiConfig config = aiConfigRepository.findByUserId(userId).orElse(new AiConfig());

        config.setUserId(userId);
        if (dto.getApiKey() != null && !"******".equals(dto.getApiKey())) {
            config.setApiKey(dto.getApiKey());
        }
        if (dto.getApiUrl() != null) config.setApiUrl(dto.getApiUrl());
        if (dto.getChatModel() != null) config.setChatModel(dto.getChatModel());
        if (dto.getEmbeddingModel() != null) config.setEmbeddingModel(dto.getEmbeddingModel());
        if (dto.getTemperature() != null) config.setTemperature(dto.getTemperature());
        if (dto.getMaxTokens() != null) config.setMaxTokens(dto.getMaxTokens());
        if (dto.getSystemPrompt() != null) config.setSystemPrompt(dto.getSystemPrompt());

        if (config.getApiUrl() == null) config.setApiUrl(defaultApiUrl);
        if (config.getChatModel() == null) config.setChatModel(defaultModel);
        if (config.getEmbeddingModel() == null) config.setEmbeddingModel(defaultEmbeddingModel);
        if (config.getTemperature() == null) config.setTemperature(0.7);
        if (config.getMaxTokens() == null) config.setMaxTokens(2000);

        aiConfigRepository.save(config);

        AiConfigDTO result = new AiConfigDTO();
        result.setApiKey("******");
        result.setApiUrl(config.getApiUrl());
        result.setChatModel(config.getChatModel());
        result.setEmbeddingModel(config.getEmbeddingModel());
        result.setTemperature(config.getTemperature());
        result.setMaxTokens(config.getMaxTokens());
        result.setSystemPrompt(config.getSystemPrompt());
        return Result.success(result);
    }

    @DeleteMapping
    @Operation(summary = "重置为默认 AI 配置")
    public Result<Void> resetConfig(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        aiConfigRepository.deleteByUserId(userId);
        return Result.success("已重置为默认配置");
    }
}