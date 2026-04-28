package com.example.kaoyan.dto;

import lombok.Data;

/**
 * 管理员更新单个环节 API 配置的请求体。
 *
 * apiKey 字段三态语义：
 *   - null         保持不变（不修改原 key）
 *   - 空字符串 ""   清除 key
 *   - 非空字符串    替换为新 key
 *
 * 其他字段同样：null 表示保持不变；如需清空请传空字符串。
 */
@Data
public class SystemApiConfigRequest {

    private String apiUrl;
    private String apiKey;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private String systemPrompt;
    private Boolean enabled;
    private String description;
}
