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

    /** 图片 base64 编码（拍照搜题时传入，可选） */
    private String image;
}
