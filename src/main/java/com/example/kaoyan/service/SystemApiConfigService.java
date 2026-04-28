package com.example.kaoyan.service;

import com.example.kaoyan.dto.SystemApiConfigDTO;
import com.example.kaoyan.dto.SystemApiConfigRequest;
import com.example.kaoyan.entity.SystemApiConfig;
import com.example.kaoyan.repository.SystemApiConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 系统级 API 配置服务。
 *
 * 提供两类能力：
 *   1. 管理员维护 4 个环节的配置（list / update）
 *   2. 业务侧（LlmService）以 stage 为粒度查询当前生效的 url / key / model 等
 *
 * 解析链：用户 AiConfig（在 LlmService 中）→ 本表 → application.yml
 */
@Service
@RequiredArgsConstructor
public class SystemApiConfigService {

    public static final String STAGE_CHAT       = "chat";
    public static final String STAGE_MULTIMODAL = "multimodal";
    public static final String STAGE_EMBEDDING  = "embedding";
    public static final String STAGE_AUDIO      = "audio";
    public static final String STAGE_TTS        = "tts";

    public static final List<String> ALL_STAGES = Arrays.asList(
            STAGE_CHAT, STAGE_MULTIMODAL, STAGE_EMBEDDING, STAGE_AUDIO, STAGE_TTS);

    private final SystemApiConfigRepository repository;

    // ── application.yml 默认值（仅作为首次种子 + 兜底） ──
    @Value("${llm.api-key:}")        private String defaultChatApiKey;
    @Value("${llm.api-url:}")        private String defaultChatApiUrl;
    @Value("${llm.model:}")          private String defaultChatModel;

    @Value("${llm.embedding-api-key:}") private String defaultEmbeddingApiKey;
    @Value("${llm.embedding-api-url:}") private String defaultEmbeddingApiUrl;
    @Value("${llm.embedding-model:}")   private String defaultEmbeddingModel;

    @Value("${llm.audio-api-key:}")   private String defaultAudioApiKey;
    @Value("${llm.audio-api-url:}")   private String defaultAudioApiUrl;
    @Value("${llm.audio-model:gemini-3-flash-preview}") private String defaultAudioModel;

    @Value("${llm.tts-api-key:}")     private String defaultTtsApiKey;
    @Value("${llm.tts-api-url:}")     private String defaultTtsApiUrl;
    @Value("${llm.tts-model:gemini-3.1-flash-tts-preview}") private String defaultTtsModel;

    /**
     * 启动时自动播种：若数据库不存在某 stage，按 yml 默认值新建一条；
     * 已存在的不动，确保管理员的修改不会被覆盖。
     */
    @PostConstruct
    @Transactional
    public void seedDefaults() {
        for (String stage : ALL_STAGES) {
            if (repository.existsById(stage)) continue;
            SystemApiConfig c = new SystemApiConfig();
            c.setStage(stage);
            c.setEnabled(true);
            switch (stage) {
                case STAGE_CHAT, STAGE_MULTIMODAL -> {
                    c.setApiUrl(defaultChatApiUrl);
                    c.setApiKey(defaultChatApiKey);
                    c.setModel(defaultChatModel);
                    c.setTemperature(0.7);
                    c.setMaxTokens(2000);
                    c.setDescription(stage.equals(STAGE_CHAT)
                            ? "文本对话（AI 答疑、标题生成、题目打标）"
                            : "多模态对话（拍照搜题、题目结构化）");
                }
                case STAGE_EMBEDDING -> {
                    // embedding 若 yml 留空则继承 chat
                    c.setApiUrl(isBlank(defaultEmbeddingApiUrl) ? defaultChatApiUrl : defaultEmbeddingApiUrl);
                    c.setApiKey(isBlank(defaultEmbeddingApiKey) ? defaultChatApiKey : defaultEmbeddingApiKey);
                    c.setModel(defaultEmbeddingModel);
                    c.setDescription("向量化（RAG 检索、文档切片、题目向量化）");
                }
                case STAGE_AUDIO -> {
                    // audio 走 Gemini 原生 generateContent，不要把 url 拼成 OpenAI 兼容形态。
                    // 留空则继承 chat 的 url（再由 GeminiNativeService 自动剥掉 /openai 后缀）。
                    c.setApiUrl(isBlank(defaultAudioApiUrl) ? defaultChatApiUrl : defaultAudioApiUrl);
                    c.setApiKey(isBlank(defaultAudioApiKey) ? defaultChatApiKey : defaultAudioApiKey);
                    c.setModel(defaultAudioModel);
                    c.setDescription("语音识别（Gemini 原生 generateContent + inlineData）");
                }
                case STAGE_TTS -> {
                    c.setApiUrl(isBlank(defaultTtsApiUrl) ? defaultChatApiUrl : defaultTtsApiUrl);
                    c.setApiKey(isBlank(defaultTtsApiKey) ? defaultChatApiKey : defaultTtsApiKey);
                    c.setModel(defaultTtsModel);
                    c.setDescription("语音合成（Gemini TTS：Kore/Puck/Zephyr 等 30 种音色）");
                }
            }
            repository.save(c);
        }
    }

    // ==================== 管理员接口 ====================

    public List<SystemApiConfigDTO> listAll() {
        return ALL_STAGES.stream()
                .map(stage -> repository.findById(stage)
                        .map(SystemApiConfigDTO::from)
                        .orElseGet(() -> {
                            // 数据库还没有，返回一条空骨架便于前端渲染
                            SystemApiConfigDTO d = new SystemApiConfigDTO();
                            d.setStage(stage);
                            d.setEnabled(false);
                            d.setApiKeySet(false);
                            return d;
                        }))
                .collect(Collectors.toList());
    }

    @Transactional
    public SystemApiConfigDTO update(String stage, SystemApiConfigRequest request) {
        if (!ALL_STAGES.contains(stage)) {
            throw new IllegalArgumentException("未知环节：" + stage);
        }
        SystemApiConfig entity = repository.findById(stage).orElseGet(() -> {
            SystemApiConfig c = new SystemApiConfig();
            c.setStage(stage);
            c.setEnabled(true);
            return c;
        });

        // 字符串字段：null 表示不变；空串表示清空（apiKey 同样语义）
        if (request.getApiUrl()       != null) entity.setApiUrl(emptyToNull(request.getApiUrl()));
        if (request.getApiKey()       != null) entity.setApiKey(emptyToNull(request.getApiKey()));
        if (request.getModel()        != null) entity.setModel(emptyToNull(request.getModel()));
        if (request.getSystemPrompt() != null) entity.setSystemPrompt(emptyToNull(request.getSystemPrompt()));
        if (request.getDescription()  != null) entity.setDescription(emptyToNull(request.getDescription()));

        if (request.getTemperature()  != null) entity.setTemperature(request.getTemperature());
        if (request.getMaxTokens()    != null) entity.setMaxTokens(request.getMaxTokens());
        if (request.getEnabled()      != null) entity.setEnabled(request.getEnabled());

        return SystemApiConfigDTO.from(repository.save(entity));
    }

    // ==================== 业务侧（LlmService 用） ====================

    /**
     * 取某 stage 当前生效的 entity（仅供同包业务读取，含 apiKey 原文）。
     * 若该 stage 被禁用或不存在，自动按规则回退：multimodal/embedding/audio → chat。
     */
    public Optional<SystemApiConfig> resolveActive(String stage) {
        Optional<SystemApiConfig> direct = repository.findById(stage)
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()));
        if (direct.isPresent()) return direct;
        // 回退到 chat（chat 自身禁用则真正没有可用配置）
        if (!STAGE_CHAT.equals(stage)) {
            return repository.findById(STAGE_CHAT).filter(c -> Boolean.TRUE.equals(c.getEnabled()));
        }
        return Optional.empty();
    }

    public String getApiUrl(String stage) {
        return resolveActive(stage).map(SystemApiConfig::getApiUrl).orElse(null);
    }

    public String getApiKey(String stage) {
        return resolveActive(stage).map(SystemApiConfig::getApiKey).orElse(null);
    }

    public String getModel(String stage) {
        return resolveActive(stage).map(SystemApiConfig::getModel).orElse(null);
    }

    public Double getTemperature(String stage) {
        return resolveActive(stage).map(SystemApiConfig::getTemperature).orElse(null);
    }

    public Integer getMaxTokens(String stage) {
        return resolveActive(stage).map(SystemApiConfig::getMaxTokens).orElse(null);
    }

    public String getSystemPrompt(String stage) {
        return resolveActive(stage).map(SystemApiConfig::getSystemPrompt).orElse(null);
    }

    // ==================== 小工具 ====================

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        return s.isBlank() ? null : s;
    }
}
