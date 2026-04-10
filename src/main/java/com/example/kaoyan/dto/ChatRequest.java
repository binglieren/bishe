package com.example.kaoyan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 对话请求 DTO
 */
@Data
public class ChatRequest {

    private Long sessionId;

    @NotBlank(message = "消息内容不能为空")
    private String message;
}
