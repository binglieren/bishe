package com.example.kaoyan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 / 更新知识库请求
 */
@Data
public class KnowledgeBaseRequest {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称不能超过 100 个字符")
    private String name;

    @Size(max = 500, message = "描述不能超过 500 个字符")
    private String description;
}
