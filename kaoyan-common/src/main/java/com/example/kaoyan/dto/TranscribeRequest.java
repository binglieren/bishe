package com.example.kaoyan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 语音识别请求 DTO
 */
@Data
public class TranscribeRequest {

    /** 录音文件 base64（不含 data: 前缀） */
    @NotBlank(message = "音频数据不能为空")
    private String audio;

    /** 音频扩展名：m4a / mp3 / wav / webm 等，默认 m4a（expo-av 录音格式） */
    private String format;
}
