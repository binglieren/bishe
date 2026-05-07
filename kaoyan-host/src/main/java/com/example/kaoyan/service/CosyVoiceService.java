package com.example.kaoyan.service;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.utils.Constants;
import com.example.kaoyan.util.AgentDebugLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.util.Base64;

/**
 * 阿里云 CosyVoice TTS 服务（基于 DashScope WebSocket SDK）。
 *
 * 使用 SpeechSynthesizer（WebSocket）替代旧 HTTP API，避免 OSS 下载 403 问题。
 *
 * 配置来源：tts stage 的 SystemApiConfig（apiKey / model）。
 */
@Service
@RequiredArgsConstructor
public class CosyVoiceService {

    public static final String DEFAULT_MODEL = "cosyvoice-v3.5-flash";
    public static final String DEFAULT_VOICE = "longanyang";

    private final SystemApiConfigService systemApiConfigService;

    /** 启动时设置 SDK 端点（只需设一次）。 */
    @PostConstruct
    public void init() {
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
        Constants.baseWebsocketApiUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
    }

    /**
     * 文本转语音。返回 MP3 格式的 base64 字符串（不含 data: 前缀）。
     *
     * @param text      要朗读的文本
     * @param voiceName 音色名（longwan / longxiaochun …），传 null 用默认 longanyang
     * @return          base64 MP3 音频
     */
    public String synthesizeSpeech(String text, String voiceName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("待合成文本为空");
        }
        String stage = SystemApiConfigService.STAGE_TTS;
        String apiKey = systemApiConfigService.getApiKey(stage);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("TTS 环节未配置 API Key");
        }
        String model = systemApiConfigService.getModel(stage);
        if (model == null || model.isBlank()) model = DEFAULT_MODEL;
        String voice = (voiceName == null || voiceName.isBlank()) ? DEFAULT_VOICE : voiceName;

        SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                .apiKey(apiKey)
                .model(model)
                .voice(voice)
                .build();

        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
        try {
            AgentDebugLog.ndjson("T1", "CosyVoiceService.synthesizeSpeech",
                    "calling TTS WS",
                    "{\"model\":\"" + model + "\",\"voice\":\"" + voice + "\",\"len\":" + text.length() + "}");
            ByteBuffer audio = synthesizer.call(text);
            if (audio == null || audio.remaining() == 0) {
                throw new RuntimeException("CosyVoice 未返回音频数据");
            }
            byte[] bytes = new byte[audio.remaining()];
            audio.get(bytes);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            AgentDebugLog.ndjson("T1err", "CosyVoiceService.synthesizeSpeech",
                    e.getClass().getSimpleName(),
                    "{\"msg\":\"" + (e.getMessage() == null ? "" : e.getMessage().replace("\"", "'")) + "\"}");
            throw new RuntimeException("CosyVoice 语音合成失败：" + e.getMessage(), e);
        } finally {
            try { synthesizer.getDuplexApi().close(1000, "bye"); } catch (Exception ignored) {}
        }
    }

    /** 当前 TTS 输出的 MIME 类型。CosyVoice V3 默认输出 MP3。 */
    public static String mimeType() {
        return "audio/mp3";
    }
}
