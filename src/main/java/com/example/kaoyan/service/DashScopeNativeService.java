package com.example.kaoyan.service;

import com.example.kaoyan.util.AgentDebugLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 阿里云百炼（DashScope）原生 API 客户端。
 *
 * 当前职责：
 *   · synthesizeSpeech (TTS) — CosyVoice 非实时 HTTP API
 *
 * 解析配置：tts stage 的 SystemApiConfig（apiUrl + apiKey + model）
 *
 * 注意：CosyVoice 同步 HTTP 不直接返回音频字节，而是返回一个 24h 下载 URL，
 *       本服务负责自动二次拉取并转换为 base64 给前端使用。
 */
@Service
@RequiredArgsConstructor
public class DashScopeNativeService {

    public static final String DEFAULT_TTS_MODEL = "cosyvoice-v3-flash";
    // 官方文档示例用的就是这个，cosyvoice-v3-flash 一定支持
    public static final String DEFAULT_TTS_VOICE = "longanyang";
    public static final int    DEFAULT_TTS_SAMPLE_RATE = 24_000;
    // wav 是官方 curl 示例使用的格式，CosyVoice 各版本都稳定支持
    public static final String DEFAULT_TTS_FORMAT = "wav";

    private final WebClient.Builder webClientBuilder;
    private final SystemApiConfigService systemApiConfigService;

    /**
     * 调用 CosyVoice 合成语音，返回带 mime 提示的 base64 字符串。
     *
     * @param text       要朗读的文本
     * @param voiceName  音色名（longwan / longxiaochun …），传 null 用默认
     * @return           base64 字符串（无 data: 前缀），mime 见 mimeType()
     */
    public String synthesizeSpeech(String text, String voiceName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("待合成文本为空");
        }
        String stage = SystemApiConfigService.STAGE_TTS;
        String baseUrl = requireUrl(stage);
        String apiKey  = requireKey(stage);
        String model   = orDefault(systemApiConfigService.getModel(stage), DEFAULT_TTS_MODEL);
        String voice   = orDefault(voiceName, DEFAULT_TTS_VOICE);

        Map<String, Object> body = Map.of(
                "model", model,
                "input", Map.of(
                        "text", text,
                        "voice", voice,
                        "format", DEFAULT_TTS_FORMAT,
                        "sample_rate", DEFAULT_TTS_SAMPLE_RATE
                )
        );

        Map response;
        try {
            AgentDebugLog.ndjson("T1", "DashScopeNativeService.synthesizeSpeech",
                    "calling CosyVoice",
                    "{\"model\":\"" + model + "\",\"voice\":\"" + voice + "\",\"len\":" + text.length() + "}");
            // 用 exchangeToMono 手动取响应：无论 2xx/4xx/5xx 都把 body 读出来
            // 这样能 100% 把 DashScope 真实错误体（含 code/message）暴露给上层
            response = webClientBuilder.baseUrl(baseUrl)
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                    .build()
                    .post()
                    .uri("/services/audio/tts/SpeechSynthesizer")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .exchangeToMono(resp -> {
                        int status = resp.statusCode().value();
                        if (status >= 200 && status < 300) {
                            return resp.bodyToMono(Map.class);
                        }
                        return resp.bodyToMono(String.class)
                                .defaultIfEmpty("(empty)")
                                .map(rawBody -> {
                                    AgentDebugLog.ndjson("T1err",
                                            "DashScopeNativeService.synthesizeSpeech",
                                            "HTTP " + status,
                                            "{\"body\":\"" + shortBody(rawBody) + "\"}");
                                    throw new RuntimeException(
                                            "CosyVoice 返回 " + status + "：" + shortBody(rawBody));
                                });
                    })
                    .block();
        } catch (Exception ex) {
            // 已经是包装好消息的 RuntimeException，直接外抛即可
            if (ex instanceof RuntimeException re && re.getMessage() != null
                    && re.getMessage().startsWith("CosyVoice 返回")) {
                throw re;
            }
            AgentDebugLog.ndjson("T1err", "DashScopeNativeService.synthesizeSpeech",
                    ex.getClass().getSimpleName(),
                    "{\"msg\":\"" + shortBody(ex.getMessage()) + "\"}");
            throw new RuntimeException("CosyVoice 调用异常：" + ex.getMessage(), ex);
        }

        String audioUrl = extractAudioUrl(response);
        if (audioUrl == null || audioUrl.isBlank()) {
            throw new RuntimeException("CosyVoice 未返回 audio_url");
        }

        // 二次拉取音频字节
        byte[] audioBytes = webClientBuilder.build()
                .get()
                .uri(audioUrl)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
        if (audioBytes == null || audioBytes.length == 0) {
            throw new RuntimeException("CosyVoice audio_url 下载结果为空");
        }
        return Base64.getEncoder().encodeToString(audioBytes);
    }

    /** 当前 TTS 输出的 MIME 类型 — 跟随 DEFAULT_TTS_FORMAT 同步 */
    public static String mimeType() {
        return switch (DEFAULT_TTS_FORMAT) {
            case "mp3"  -> "audio/mp3";
            case "wav"  -> "audio/wav";
            case "pcm"  -> "audio/pcm";
            case "opus" -> "audio/opus";
            default     -> "audio/mp3";
        };
    }

    // ──────────────────────────────────────────────
    //  内部工具
    // ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static String extractAudioUrl(Map response) {
        if (response == null) return null;
        // CosyVoice 同步响应：{"output":{"audio":{"url":"..."}},"usage":{...},"request_id":"..."}
        Object output = response.get("output");
        if (output instanceof Map<?, ?> out) {
            Object audio = out.get("audio");
            if (audio instanceof Map<?, ?> aud) {
                Object url = aud.get("url");
                if (url != null) return url.toString();
                // 极少数版本可能直接给 url 字段
                Object alt = aud.get("data");
                if (alt != null) return alt.toString();
            }
            // 备选 path：output.url
            Object directUrl = out.get("audio_url");
            if (directUrl != null) return directUrl.toString();
        }
        return null;
    }

    private String requireUrl(String stage) {
        String url = systemApiConfigService.getApiUrl(stage);
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(stage + " 环节未配置 API URL（请在 admin 后台填写）");
        }
        url = url.trim();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        // 规整：用户可能填了 /compatible-mode/v1（错），帮他纠到 /api/v1
        if (url.endsWith("/compatible-mode/v1")) {
            url = url.substring(0, url.length() - "/compatible-mode/v1".length()) + "/api/v1";
        }
        return url;
    }

    private String requireKey(String stage) {
        String key = systemApiConfigService.getApiKey(stage);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(stage + " 环节未配置 API Key（请在 admin 后台填写）");
        }
        return key;
    }

    private static String orDefault(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static String shortBody(String body) {
        if (body == null) return "";
        String s = body.replace("\"", "'").replace("\n", " ");
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
