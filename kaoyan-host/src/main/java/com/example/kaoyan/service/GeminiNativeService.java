package com.example.kaoyan.service;

import com.example.kaoyan.util.AgentDebugLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Gemini 原生 API 客户端（区别于 OpenAI 兼容端点）。
 *
 * 路径形态：{base}/models/{model}:generateContent，与 /chat/completions 不同，
 * 因此不能复用 LlmService 的 OpenAI-shape 逻辑。
 *
 * 当前提供两个能力：
 *   1. transcribeAudio  - 语音识别（audio stage 配置驱动）
 *   2. synthesizeSpeech - 语音合成（tts   stage 配置驱动）
 *
 * 解析配置链同 LlmService：用户 AiConfig（暂未支持 audio/tts 字段）→ SystemApiConfig → yml 默认
 * 这里只读 SystemApiConfig；用户级覆盖以后再加。
 */
@Service
@RequiredArgsConstructor
public class GeminiNativeService {

    private final WebClient.Builder webClientBuilder;
    private final SystemApiConfigService systemApiConfigService;

    // ──────────────────────────────────────────────────
    //  Speech-To-Text（用 Gemini 多模态 generateContent）
    // ──────────────────────────────────────────────────

    /**
     * 用 Gemini 原生 API 把音频 base64 转成文字。
     *
     * @param audioBase64 录音 base64（不含 data: 前缀）
     * @param format      扩展名，如 m4a/mp3/wav/webm；用于推断 mime
     * @param userId      用户 id（保留参数，未来用户级配置）
     * @return            转写文本
     */
    public String transcribeAudio(String audioBase64, String format, Long userId) {
        if (audioBase64 == null || audioBase64.isBlank()) {
            throw new IllegalArgumentException("音频数据为空");
        }
        String stage = SystemApiConfigService.STAGE_AUDIO;
        String baseUrl = requireUrl(stage);
        String apiKey  = requireKey(stage);
        String model   = orDefault(systemApiConfigService.getModel(stage), "gemini-3-flash-preview");

        String mime = audioMime(format);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(
                                Map.of("text",
                                        "Generate a verbatim transcript of the speech. " +
                                        "Return only the transcribed text, no commentary, no labels, no timestamps."),
                                Map.of("inlineData", Map.of(
                                        "mimeType", mime,
                                        "data", audioBase64
                                ))
                        )
                ))
        );

        Map response;
        try {
            AgentDebugLog.ndjson("A1", "GeminiNativeService.transcribeAudio", "calling STT",
                    "{\"model\":\"" + model + "\",\"mime\":\"" + mime + "\"}");
            response = webClientBuilder.baseUrl(baseUrl)
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(25 * 1024 * 1024))
                    .build()
                    .post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException wcre) {
            AgentDebugLog.ndjson("A1err", "GeminiNativeService.transcribeAudio", "WebClientResponseException",
                    "{\"status\":" + wcre.getStatusCode().value() + "}");
            throw wcre;
        } catch (Exception ex) {
            AgentDebugLog.ndjson("A1err", "GeminiNativeService.transcribeAudio",
                    ex.getClass().getSimpleName(), "{}");
            throw ex;
        }

        return extractFirstTextPart(response);
    }

    // ──────────────────────────────────────────────────
    //  Text-To-Speech（用 Gemini TTS 模型）
    // ──────────────────────────────────────────────────

    /** Gemini TTS 默认采样参数：24kHz / 16-bit / mono，依官方文档不变 */
    public static final int    TTS_SAMPLE_RATE = 24_000;
    public static final int    TTS_BITS_PER_SAMPLE = 16;
    public static final int    TTS_CHANNELS = 1;
    public static final String TTS_DEFAULT_VOICE = "Kore";
    public static final String TTS_DEFAULT_MODEL = "gemini-2.5-flash-preview-tts";

    /**
     * 文本转语音。返回结果带 WAV 头，可直接被前端 expo-av / 浏览器 Audio 播放。
     *
     * @param text       要朗读的文本
     * @param voiceName  voice 名（Kore/Puck/Zephyr 等 30 种），传 null 用默认
     * @param userId     用户 id（保留）
     * @return           带 WAV 头的 base64 字符串（可直接拼成 data:audio/wav;base64,xxx）
     */
    public String synthesizeSpeech(String text, String voiceName, Long userId) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("待合成文本为空");
        }
        String stage   = SystemApiConfigService.STAGE_TTS;
        String baseUrl = requireUrl(stage);
        String apiKey  = requireKey(stage);
        String model   = orDefault(systemApiConfigService.getModel(stage), TTS_DEFAULT_MODEL);
        String voice   = (voiceName == null || voiceName.isBlank()) ? TTS_DEFAULT_VOICE : voiceName;

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", text))
                )),
                "generationConfig", Map.of(
                        "responseModalities", List.of("AUDIO"),
                        "speechConfig", Map.of(
                                "voiceConfig", Map.of(
                                        "prebuiltVoiceConfig", Map.of(
                                                "voiceName", voice
                                        )
                                )
                        )
                )
        );

        Map response;
        try {
            AgentDebugLog.ndjson("T1", "GeminiNativeService.synthesizeSpeech", "calling TTS",
                    "{\"model\":\"" + model + "\",\"voice\":\"" + voice + "\",\"len\":" + text.length() + "}");
            response = webClientBuilder.baseUrl(baseUrl)
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(25 * 1024 * 1024))
                    .build()
                    .post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException wcre) {
            AgentDebugLog.ndjson("T1err", "GeminiNativeService.synthesizeSpeech",
                    "WebClientResponseException",
                    "{\"status\":" + wcre.getStatusCode().value() + "}");
            throw wcre;
        } catch (Exception ex) {
            AgentDebugLog.ndjson("T1err", "GeminiNativeService.synthesizeSpeech",
                    ex.getClass().getSimpleName(), "{}");
            throw ex;
        }

        String pcmBase64 = extractFirstInlineDataBase64(response);
        if (pcmBase64 == null || pcmBase64.isBlank()) {
            throw new RuntimeException("TTS 未返回音频数据");
        }
        byte[] pcm = Base64.getDecoder().decode(pcmBase64);
        byte[] wav = pcmToWav(pcm, TTS_SAMPLE_RATE, TTS_CHANNELS, TTS_BITS_PER_SAMPLE);
        return Base64.getEncoder().encodeToString(wav);
    }

    // ──────────────────────────────────────────────────
    //  内部工具
    // ──────────────────────────────────────────────────

    /**
     * 给一段 PCM 数据加 44 字节标准 WAV (RIFF/WAVE/fmt /data) 头。
     * 这样前端不需要 PCM 解码器，直接用 audio 标签 / expo-av 即可播放。
     */
    private static byte[] pcmToWav(byte[] pcm, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcm.length;
        int chunkSize = 36 + dataSize;

        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes());
        header.putInt(chunkSize);
        header.put("WAVE".getBytes());
        header.put("fmt ".getBytes());
        header.putInt(16);                      // Subchunk1Size for PCM
        header.putShort((short) 1);             // AudioFormat = 1 (PCM)
        header.putShort((short) channels);
        header.putInt(sampleRate);
        header.putInt(byteRate);
        header.putShort((short) blockAlign);
        header.putShort((short) bitsPerSample);
        header.put("data".getBytes());
        header.putInt(dataSize);

        byte[] result = new byte[44 + pcm.length];
        System.arraycopy(header.array(), 0, result, 0, 44);
        System.arraycopy(pcm, 0, result, 44, pcm.length);
        return result;
    }

    /** 从 candidates[0].content.parts 中取第一个 text 字段 */
    @SuppressWarnings("unchecked")
    private static String extractFirstTextPart(Map response) {
        if (response == null) throw new RuntimeException("Gemini 返回空");
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("Gemini 未返回 candidates");
        }
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) throw new RuntimeException("Gemini 返回 content 为空");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) throw new RuntimeException("Gemini 返回 parts 为空");
        Object text = parts.get(0).get("text");
        return text == null ? "" : text.toString().trim();
    }

    /** 从 candidates[0].content.parts[0].inlineData.data 中取 base64 音频 */
    @SuppressWarnings("unchecked")
    private static String extractFirstInlineDataBase64(Map response) {
        if (response == null) return null;
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) return null;
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) return null;
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) return null;
        Map<String, Object> inlineData = (Map<String, Object>) parts.get(0).get("inlineData");
        if (inlineData == null) inlineData = (Map<String, Object>) parts.get(0).get("inline_data");
        if (inlineData == null) return null;
        Object data = inlineData.get("data");
        return data == null ? null : data.toString();
    }

    private String requireUrl(String stage) {
        String url = systemApiConfigService.getApiUrl(stage);
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(stage + " 环节未配置 API URL（请在 admin 后台填写）");
        }
        // 去掉尾部 / 与可能错误带上的 /openai 后缀（Gemini Native 不要走兼容端点）
        url = url.trim();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (url.endsWith("/openai")) url = url.substring(0, url.length() - "/openai".length());
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

    private static String audioMime(String format) {
        if (format == null || format.isBlank()) return "audio/mp3";
        String f = format.toLowerCase().replace(".", "");
        return switch (f) {
            case "wav"        -> "audio/wav";
            case "mp3"        -> "audio/mp3";
            case "aiff"       -> "audio/aiff";
            case "aac"        -> "audio/aac";
            case "ogg"        -> "audio/ogg";
            case "flac"       -> "audio/flac";
            // m4a 是 MP4 容器（不是裸 AAC），用 audio/mp4 才能让 Gemini 正确解析
            case "m4a", "mp4" -> "audio/mp4";
            // 浏览器录的 webm，Gemini 可识别（实测 ok）
            case "webm"       -> "audio/webm";
            default           -> "audio/mp3";
        };
    }
}
