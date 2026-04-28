package com.example.kaoyan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 头像上传请求
 */
@Data
public class AvatarUploadRequest {

    /** 图片 base64 字符串（可带或不带 data: 前缀） */
    @NotBlank(message = "头像数据不能为空")
    private String image;
}
