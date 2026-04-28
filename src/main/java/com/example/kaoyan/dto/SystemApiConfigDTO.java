package com.example.kaoyan.dto;

import com.example.kaoyan.entity.SystemApiConfig;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统级 API 配置返回 DTO。
 *
 * 安全策略：
 *   - 永远不返回 apiKey 原文
 *   - apiKeySet：标识当前是否已配置 key
 *   - apiKeyPreview：脱敏预览，前 4 + … + 后 4 字符（不足 8 位则全部用 ***）
 */
@Data
public class SystemApiConfigDTO {

    private String stage;
    private String apiUrl;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private String systemPrompt;
    private Boolean enabled;
    private String description;

    /** API Key 是否已配置 */
    private Boolean apiKeySet;

    /** API Key 脱敏预览，仅用于让管理员确认是哪一把 key */
    private String apiKeyPreview;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SystemApiConfigDTO from(SystemApiConfig entity) {
        SystemApiConfigDTO dto = new SystemApiConfigDTO();
        dto.setStage(entity.getStage());
        dto.setApiUrl(entity.getApiUrl());
        dto.setModel(entity.getModel());
        dto.setTemperature(entity.getTemperature());
        dto.setMaxTokens(entity.getMaxTokens());
        dto.setSystemPrompt(entity.getSystemPrompt());
        dto.setEnabled(entity.getEnabled());
        dto.setDescription(entity.getDescription());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        String key = entity.getApiKey();
        boolean set = key != null && !key.isBlank();
        dto.setApiKeySet(set);
        dto.setApiKeyPreview(set ? maskKey(key) : null);
        return dto;
    }

    private static String maskKey(String key) {
        if (key == null) return null;
        String trimmed = key.trim();
        if (trimmed.length() <= 8) {
            return "********";
        }
        return trimmed.substring(0, 4) + "…" + trimmed.substring(trimmed.length() - 4);
    }
}
