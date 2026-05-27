package com.example.kaoyan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 图片作答请求 DTO（简答题拍照上传）
 */
@Data
public class ImageAnswerRequest {

    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    private String imageBase64;

    private String userAnswer;
}
