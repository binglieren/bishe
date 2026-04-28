package com.example.kaoyan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 语音合成请求 DTO
 */
@Data
public class TtsRequest {

    /** 要朗读的文本（建议 < 5000 字符以控制响应时延和带宽） */
    @NotBlank(message = "待合成文本不能为空")
    @Size(max = 8000, message = "文本过长")
    private String text;

    /** 音色名（Kore/Puck/Zephyr 等 30 种），可选，留空用默认 Kore */
    private String voiceName;
}
