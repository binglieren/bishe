package com.example.kaoyan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交答案请求 DTO
 */
@Data
public class AnswerRequest {

    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    private String userAnswer;
}
