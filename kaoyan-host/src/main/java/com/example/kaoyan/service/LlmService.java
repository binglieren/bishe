package com.example.kaoyan.service;

import com.example.kaoyan.dto.StreamChatEvent;
import com.example.kaoyan.entity.AiConfig;
import com.example.kaoyan.repository.AiConfigRepository;
import com.example.kaoyan.util.AgentDebugLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmService {

    private final WebClient.Builder webClientBuilder;
    private final AiConfigRepository aiConfigRepository;
    private final SystemApiConfigService systemApiConfigService;
    private final GeminiNativeService geminiNativeService;
    private final CosyVoiceService cosyVoiceService;

    @Value("${llm.api-key}")
    private String defaultApiKey;

    @Value("${llm.api-url}")
    private String defaultApiUrl;

    @Value("${llm.model}")
    private String defaultModel;

    @Value("${llm.embedding-model:}")
    private String defaultEmbeddingModel;

    @Value("${llm.embedding-api-url:}")
    private String defaultEmbeddingApiUrl;

    @Value("${llm.embedding-api-key:}")
    private String defaultEmbeddingApiKey;

    // 注：audio / tts 走 GeminiNativeService，不再使用 yml 兜底字段。

    private AiConfig getUserConfig(Long userId) {
        if (userId == null) return null;
        return aiConfigRepository.findByUserId(userId).orElse(null);
    }

    private String resolveApiKey(Long userId) {
        return resolveApiKey(userId, SystemApiConfigService.STAGE_CHAT);
    }

    /**
     * 解析链：用户 AiConfig（仅 chat 阶段） → 系统 SystemApiConfig（按 stage 严格隔离） → application.yml
     *
     * 严格隔离策略：
     *   - chat            可被用户 AiConfig 覆盖
     *   - 其他 stage      不接受用户级覆盖；只看管理员的 SystemApiConfig
     *   （embedding 走专门的 resolveEmbedding* 方法）
     */
    private String resolveApiKey(Long userId, String stage) {
        if (SystemApiConfigService.STAGE_CHAT.equals(stage)) {
            AiConfig config = getUserConfig(userId);
            if (config != null && config.getApiKey() != null && !config.getApiKey().isBlank()) {
                return config.getApiKey();
            }
        }
        String sys = systemApiConfigService.getApiKey(stage);
        if (sys != null && !sys.isBlank()) return sys;
        return defaultApiKey;
    }

    /**
     * 规范化 base URL：把用户可能误粘进来的具体端点后缀剥掉，只留下 base。
     * LlmService.chat()/getEmbedding() 后续会自己拼 /chat/completions 或 /embeddings。
     */
    private String normalizeApiUrl(String url) {
        if (url == null) return null;
        url = url.trim();
        // 常见误粘后缀（按从长到短依次去除一次即可）
        String[] suffixes = {
                "/chat/completions",
                "/completions",
                "/embeddings",
                "/audio/transcriptions",
                "/audio/speech",
        };
        for (String s : suffixes) {
            if (url.endsWith(s)) {
                url = url.substring(0, url.length() - s.length());
                break;
            }
        }
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private String resolveApiUrl(Long userId) {
        return resolveApiUrl(userId, SystemApiConfigService.STAGE_CHAT);
    }

    private String resolveApiUrl(Long userId, String stage) {
        if (SystemApiConfigService.STAGE_CHAT.equals(stage)) {
            AiConfig config = getUserConfig(userId);
            if (config != null && config.getApiUrl() != null && !config.getApiUrl().isBlank()) {
                return normalizeApiUrl(config.getApiUrl());
            }
        }
        String sys = systemApiConfigService.getApiUrl(stage);
        if (sys != null && !sys.isBlank()) return normalizeApiUrl(sys);
        return normalizeApiUrl(defaultApiUrl);
    }

    private String resolveChatModel(Long userId) {
        return resolveChatModel(userId, SystemApiConfigService.STAGE_CHAT);
    }

    /**
     * 模型解析：chat 允许用户级 chatModel 覆盖；其他 stage 严格按管理员配置。
     */
    private String resolveChatModel(Long userId, String stage) {
        if (SystemApiConfigService.STAGE_CHAT.equals(stage)) {
            AiConfig config = getUserConfig(userId);
            if (config != null && config.getChatModel() != null && !config.getChatModel().isBlank()) {
                return config.getChatModel();
            }
        }
        String sys = systemApiConfigService.getModel(stage);
        if (sys != null && !sys.isBlank()) return sys;
        return defaultModel;
    }

    private String resolveEmbeddingModel(Long userId) {
        AiConfig config = getUserConfig(userId);
        if (config != null && config.getEmbeddingModel() != null && !config.getEmbeddingModel().isBlank()) {
            return config.getEmbeddingModel();
        }
        String sys = systemApiConfigService.getModel(SystemApiConfigService.STAGE_EMBEDDING);
        if (sys != null && !sys.isBlank()) return sys;
        return defaultEmbeddingModel;
    }

    /**
     * Embedding 端点：
     *   1. 用户 embedding_api_url  (最高优先级)
     *   2. 系统默认 llm.embedding-api-url
     *   3. 退回 chat 的 api_url    (兜底)
     */
    private String resolveEmbeddingApiUrl(Long userId) {
        AiConfig config = getUserConfig(userId);
        if (config != null && config.getEmbeddingApiUrl() != null && !config.getEmbeddingApiUrl().isBlank()) {
            return normalizeApiUrl(config.getEmbeddingApiUrl());
        }
        String sys = systemApiConfigService.getApiUrl(SystemApiConfigService.STAGE_EMBEDDING);
        if (sys != null && !sys.isBlank()) return normalizeApiUrl(sys);
        if (defaultEmbeddingApiUrl != null && !defaultEmbeddingApiUrl.isBlank()) {
            return normalizeApiUrl(defaultEmbeddingApiUrl);
        }
        return resolveApiUrl(userId);
    }

    /**
     * Embedding API key：
     *   1. 用户 embedding_api_key
     *   2. 系统默认 llm.embedding-api-key
     *   3. 退回 chat 的 key
     */
    private String resolveEmbeddingApiKey(Long userId) {
        AiConfig config = getUserConfig(userId);
        if (config != null && config.getEmbeddingApiKey() != null && !config.getEmbeddingApiKey().isBlank()) {
            return config.getEmbeddingApiKey();
        }
        String sys = systemApiConfigService.getApiKey(SystemApiConfigService.STAGE_EMBEDDING);
        if (sys != null && !sys.isBlank()) return sys;
        if (defaultEmbeddingApiKey != null && !defaultEmbeddingApiKey.isBlank()) {
            return defaultEmbeddingApiKey;
        }
        return resolveApiKey(userId);
    }

    private Double resolveTemperature(Long userId) {
        AiConfig config = getUserConfig(userId);
        if (config != null && config.getTemperature() != null) {
            return config.getTemperature();
        }
        Double sys = systemApiConfigService.getTemperature(SystemApiConfigService.STAGE_CHAT);
        if (sys != null) return sys;
        return 0.7;
    }

    private Integer resolveMaxTokens(Long userId) {
        AiConfig config = getUserConfig(userId);
        if (config != null && config.getMaxTokens() != null) {
            return config.getMaxTokens();
        }
        Integer sys = systemApiConfigService.getMaxTokens(SystemApiConfigService.STAGE_CHAT);
        if (sys != null) return sys;
        return null; // 不传 max_tokens，由模型自行决定输出长度
    }

    public String resolveSystemPrompt(Long userId) {
        AiConfig config = getUserConfig(userId);
        if (config != null && config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            return config.getSystemPrompt();
        }
        String sys = systemApiConfigService.getSystemPrompt(SystemApiConfigService.STAGE_CHAT);
        if (sys != null && !sys.isBlank()) return sys;
        return "你是一个专业的考研辅导助手。请根据提供的参考资料回答用户的问题。" +
                "如果参考资料中包含相关信息，请基于资料回答并说明来源。" +
                "如果参考资料不包含相关信息，请基于你的知识回答，并告知用户这不是来自其上传的资料。";
    }

    public float[] getEmbedding(String text) {
        return getEmbedding(text, null);
    }

    /**
     * 数据库 embedding 列固定为 vector(1536)。
     * 不同厂商默认输出维度不同（OpenAI text-embedding-3-small=1536，Gemini=3072 等），
     * 这里统一显式指定 dimensions=1536，让支持该参数的厂商截断到目标维度，
     * 保证写入 pgvector 时不会维度不匹配报错。
     */
    private static final int EMBEDDING_DIMENSIONS = 1536;

    public float[] getEmbedding(String text, Long userId) {
        String url = resolveEmbeddingApiUrl(userId);
        String key = resolveEmbeddingApiKey(userId);
        String model = resolveEmbeddingModel(userId);

        WebClient client = webClientBuilder.codecs(c -> c.defaultCodecs().maxInMemorySize(25 * 1024 * 1024)).baseUrl(url).build();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", text,
                "dimensions", EMBEDDING_DIMENSIONS
        );

        Map response;
        try {
            response = client.post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException wcre) {
            // #region agent log
            AgentDebugLog.ndjson("H2err", "LlmService.getEmbedding", "WebClientResponseException",
                    "{\"status\":" + wcre.getStatusCode().value() + ",\"phase\":\"embeddings\"}");
            // #endregion
            throw wcre;
        } catch (Exception ex) {
            // #region agent log
            AgentDebugLog.ndjson("H2err", "LlmService.getEmbedding", ex.getClass().getSimpleName(), "{}");
            // #endregion
            throw ex;
        }

        if (response == null) {
            throw new RuntimeException("获取向量嵌入失败");
        }

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        List<Double> embedding = (List<Double>) data.get(0).get("embedding");

        // 维度不一致时尽早报错，避免写 pgvector 时才崩在 SQL 层
        if (embedding.size() != EMBEDDING_DIMENSIONS) {
            String msg = "Embedding 维度不匹配：期望 " + EMBEDDING_DIMENSIONS
                    + "，实际 " + embedding.size()
                    + "。请确认厂商是否支持 dimensions 参数，或调整数据库 vector 列大小。";
            AgentDebugLog.ndjson("H2err", "LlmService.getEmbedding", "DimensionMismatch",
                    "{\"expected\":" + EMBEDDING_DIMENSIONS + ",\"actual\":" + embedding.size() + "}");
            throw new RuntimeException(msg);
        }

        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.get(i).floatValue();
        }
        return result;
    }

    public String chat(List<Map<String, String>> messages) {
        return chat(messages, null);
    }

    /**
     * 多模态聊天（支持图片）
     * @param messages 消息列表，content 可以是 String 或 List（多模态）
     */
    public String chatMultimodal(List<Map<String, Object>> messages, Long userId) {
        // 多模态走 multimodal stage（在系统配置中独立可配；未配置则回退到 chat）
        String url = resolveApiUrl(userId, SystemApiConfigService.STAGE_MULTIMODAL);
        String key = resolveApiKey(userId, SystemApiConfigService.STAGE_MULTIMODAL);
        String model = resolveChatModel(userId, SystemApiConfigService.STAGE_MULTIMODAL);
        Double temperature = resolveTemperature(userId);
        Integer maxTokens = resolveMaxTokens(userId);

        WebClient client = webClientBuilder.codecs(c -> c.defaultCodecs().maxInMemorySize(25 * 1024 * 1024)).baseUrl(url).build();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature);
        if (maxTokens != null) requestBody.put("max_tokens", maxTokens);

        Map response;
        try {
            response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException wcre) {
            AgentDebugLog.ndjson("H4err", "LlmService.chatMultimodal", "WebClientResponseException",
                    "{\"status\":" + wcre.getStatusCode().value() + ",\"phase\":\"chat\"}");
            throw wcre;
        } catch (Exception ex) {
            AgentDebugLog.ndjson("H4err", "LlmService.chatMultimodal", ex.getClass().getSimpleName(), "{}");
            throw ex;
        }

        if (response == null) {
            throw new RuntimeException("LLM 调用失败");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    public String chat(List<Map<String, String>> messages, Long userId) {
        return chat(messages, userId, false);
    }

    public String chat(List<Map<String, String>> messages, Long userId, boolean thinkingEnabled) {
        String url = resolveApiUrl(userId);
        String key = resolveApiKey(userId);
        String model = resolveChatModel(userId);
        Double temperature = resolveTemperature(userId);
        Integer maxTokens = resolveMaxTokens(userId);

        WebClient client = webClientBuilder.codecs(c -> c.defaultCodecs().maxInMemorySize(25 * 1024 * 1024)).baseUrl(url).build();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature);
        if (maxTokens != null) requestBody.put("max_tokens", maxTokens);
        if (thinkingEnabled) {
            requestBody.put("thinking", Map.of("type", "enabled"));
        }

        Map response;
        try {
            response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException wcre) {
            // #region agent log
            AgentDebugLog.ndjson("H4err", "LlmService.chat", "WebClientResponseException",
                    "{\"status\":" + wcre.getStatusCode().value() + ",\"phase\":\"chat\"}");
            // #endregion
            throw wcre;
        } catch (Exception ex) {
            // #region agent log
            AgentDebugLog.ndjson("H4err", "LlmService.chat", ex.getClass().getSimpleName(), "{}");
            // #endregion
            throw ex;
        }

        if (response == null) {
            throw new RuntimeException("LLM 调用失败");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    /**
     * 流式 chat（SSE）。返回 Flux<StreamChatEvent>，区分 reasoning_content 和 content。
     */
    public Flux<StreamChatEvent> chatStream(List<Map<String, String>> messages, Long userId) {
        return chatStream(messages, userId, false);
    }

    /** 流式 chat + thinking 模式 */
    public Flux<StreamChatEvent> chatStream(List<Map<String, String>> messages, Long userId, boolean thinkingEnabled) {
        String url = resolveApiUrl(userId);
        String key = resolveApiKey(userId);
        String model = resolveChatModel(userId);
        Double temperature = resolveTemperature(userId);
        Integer maxTokens = resolveMaxTokens(userId);

        System.out.println("chatStream: url=" + url + " model=" + model + " maxTokens=" + maxTokens + " thinking=" + thinkingEnabled);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature);
        if (maxTokens != null) requestBody.put("max_tokens", maxTokens);
        requestBody.put("stream", true);
        if (thinkingEnabled) {
            requestBody.put("thinking", Map.of("type", "enabled"));
        }

        return webClientBuilder.codecs(c -> c.defaultCodecs().maxInMemorySize(25 * 1024 * 1024)).baseUrl(url).build()
                .post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .exchangeToFlux(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToFlux(DataBuffer.class)
                                .flatMap(buffer -> {
                                    byte[] bytes = new byte[buffer.readableByteCount()];
                                    buffer.read(bytes);
                                    String chunk = new String(bytes, StandardCharsets.UTF_8);
                                    return Flux.fromArray(chunk.split("\n"));
                                })
                                .filter(line -> !line.isBlank() && line.startsWith("data: ") && !line.equals("data: [DONE]"))
                                .map(line -> line.substring(6))
                                .handle((String json, reactor.core.publisher.SynchronousSink<StreamChatEvent> sink) -> {
                                    StreamChatEvent event = extractStreamEvent(json);
                                    if (event != null && event.getText() != null && !event.getText().isEmpty()) {
                                        sink.next(event);
                                    }
                                });
                    }
                    return response.createException().flatMapMany(Flux::error);
                })
                .doOnError(e -> {
                    System.err.println("chatStream Flux error: " + e.getMessage());
                    e.printStackTrace();
                });
    }

    private final ObjectMapper streamObjectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private StreamChatEvent extractStreamEvent(String json) {
        try {
            Map<String, Object> data = streamObjectMapper.readValue(json, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
            if (delta == null) return null;

            // 先检查 reasoning_content（思考过程）
            Object reasoning = delta.get("reasoning_content");
            if (reasoning != null && !reasoning.toString().isEmpty()) {
                return StreamChatEvent.reasoning(reasoning.toString());
            }

            // 再检查 content
            Object content = delta.get("content");
            if (content != null && !content.toString().isEmpty()) {
                return StreamChatEvent.content(content.toString());
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // 保留旧版兼容方法
    public Flux<String> chatStreamRaw(List<Map<String, String>> messages, Long userId) {
        return chatStream(messages, userId).map(StreamChatEvent::getText);
    }

    /**
     * 从图片中提取结构化题目信息（用于保存到题库）
     * 使用多模态模型分析图片 + AI 解答，返回 JSON 字符串
     */
    public String extractQuestion(String imageBase64, String aiAnswer, Long userId) {
        String systemPrompt = "你是一个题目结构化解析助手。请从图片中提取题目信息，结合AI解答，返回标准JSON。只返回JSON，不含任何其他文字。";
        String userPrompt = "AI已给出如下解答：\n" + aiAnswer +
                "\n\n请从图片中提取题目结构并返回以下JSON（只返回JSON本身，不含markdown标记）：\n" +
                "{\"type\":\"单选|多选|填空|简答\",\"subject\":\"数学|英语|专业课\"," +
                "\"content\":\"题目正文（不含选项列表）\"," +
                "\"options\":[{\"label\":\"A\",\"content\":\"选项内容\",\"isCorrect\":true}]," +
                "\"answer\":\"正确答案（选择题用字母如A或AB，简答用文字）\"," +
                "\"analysis\":\"解题分析\"," +
                "\"knowledgePoints\":[\"知识点名称\"]}\n" +
                "重要：subject 字段只能取「数学」「英语」「专业课」三者之一。政治、历史、计算机、医学等专业相关题目一律归类为「专业课」。";

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        List<Map<String, Object>> contentParts = new ArrayList<>();
        contentParts.add(Map.of("type", "text", "text", userPrompt));
        contentParts.add(Map.of("type", "image_url", "image_url",
                Map.of("url", "data:image/jpeg;base64," + imageBase64)));
        messages.add(Map.of("role", "user", "content", contentParts));

        return chatMultimodal(messages, userId);
    }

    /**
     * 从 AI 解答文本中提取结构化题目（纯文本，不传图片）。
     * 相比 extractQuestion 少传一次 base64 图，节省 token 和延迟。
     */
    public String extractQuestionFromText(String aiAnswer, Long userId) {
        String systemPrompt = "你是一个题目结构化解析助手。请从AI解答文本中提取题目信息，返回标准JSON。只返回JSON，不含任何其他文字。";
        String userPrompt = "AI已给出如下解答：\n" + aiAnswer +
                "\n\n请从这段解答文本中推断并提取题目结构，返回以下JSON（只返回JSON本身，不含markdown标记）：\n" +
                "{\"type\":\"单选|多选|填空|简答\",\"subject\":\"数学|英语|专业课\"," +
                "\"content\":\"题目正文（不含选项列表）\"," +
                "\"options\":[{\"label\":\"A\",\"content\":\"选项内容\",\"isCorrect\":true}]," +
                "\"answer\":\"正确答案（选择题用字母如A或AB，简答用文字）\"," +
                "\"analysis\":\"解题分析\"," +
                "\"knowledgePoints\":[\"知识点名称\"]}\n" +
                "重要：subject 字段只能取「数学」「英语」「专业课」三者之一。政治、历史、计算机、医学等专业相关题目一律归类为「专业课」。";

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));

        return chat(messages, userId);
    }

    /**
     * 用 LLM 为题目打多标签。强制从候选知识点中选，并返回置信度。
     *
     * @param subject         科目（政治/英语/数学/专业课）
     * @param content         题目正文（含选项）
     * @param candidateKps    候选知识点名称列表（来自 knowledge_point 表，同科目）
     * @param userId          用户 id（用于解析 API 配置）
     * @return  LLM 返回的 JSON 字符串，形如
     *          {"tags":[{"name":"洛必达法则","confidence":0.95},{"name":"极限","confidence":0.70}]}
     */
    public String tagQuestion(String subject, String content, List<String> candidateKps, Long userId) {
        String candidateStr = candidateKps == null || candidateKps.isEmpty()
                ? "（无候选，可自由命名）"
                : String.join("、", candidateKps);

        String systemPrompt =
            "你是考研题目标签专家。请分析题目涉及的知识点，并从候选知识点中选择最相关的 1-5 个。\n" +
            "要求：\n" +
            "1. 必须尽量从候选知识点中选，如候选均不合适可新增（新增时请使用考研标准术语）。\n" +
            "2. 按相关性从高到低排序，首个为主标签。\n" +
            "3. 综合性题目必须标注所有相关考点（如一题涉及导数 + 中值定理 + 级数，应全部标出）。\n" +
            "4. confidence 取值 0.0-1.0，表示该标签的相关性。\n" +
            "5. 只返回 JSON，不含 markdown 代码块。";

        String userPrompt = String.format(
            "【科目】%s\n【候选知识点】%s\n【题目】\n%s\n\n请返回：\n" +
            "{\"tags\":[{\"name\":\"知识点名称\",\"confidence\":0.95}]}",
            subject == null ? "未知" : subject, candidateStr, content);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));

        return chat(messages, userId);
    }

    public String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    // ==================== 语音（STT / TTS） ====================
    //
    // STT 与 TTS 都走 Gemini 原生 generateContent + inlineData，
    // 由独立的 GeminiNativeService 实现；这里只做委托。
    // 配置完全由 admin 后台 audio / tts 两个 stage 决定，互不影响、也不受用户级 AiConfig 影响。

    /**
     * 语音识别（STT）：根据 audio stage 配置的 URL 自动选路：
     *   · dashscope.aliyuncs.com → 走 OpenAI 兼容 /chat/completions + input_audio
     *   · generativelanguage.googleapis.com → 走 Gemini Native generateContent
     *   · 其它 → 默认尝试 OpenAI 兼容路径（兼容 OpenAI / 智谱 / 其它兼容厂商）
     */
    public String transcribeAudio(String audioBase64, String format, Long userId) {
        String url = systemApiConfigService.getApiUrl(SystemApiConfigService.STAGE_AUDIO);
        if (url != null && url.contains("generativelanguage.googleapis.com")
                && !url.contains("/openai")) {
            return geminiNativeService.transcribeAudio(audioBase64, format, userId);
        }
        return transcribeAudioOpenAICompat(audioBase64, format, userId);
    }

    /**
     * OpenAI 兼容路径的 STT：把音频塞进 chat/completions 的 input_audio content 里，
     * 让多模态模型直接转写。Qwen-Omni、Gemini OpenAI-compat 均走这条路。
     */
    private String transcribeAudioOpenAICompat(String audioBase64, String format, Long userId) {
        String stage = SystemApiConfigService.STAGE_AUDIO;
        String url   = systemApiConfigService.getApiUrl(stage);
        String key   = systemApiConfigService.getApiKey(stage);
        String model = systemApiConfigService.getModel(stage);
        if (url == null || url.isBlank()) throw new IllegalStateException("audio 环节未配置 API URL");
        if (key == null || key.isBlank()) throw new IllegalStateException("audio 环节未配置 API Key");
        url = normalizeApiUrl(url);

        String fmt = (format == null || format.isBlank()) ? "mp3" : format.toLowerCase();
        // Qwen-Omni 原生支持 wav / mp3 / m4a / aac / 3gp / amr 等；按原扩展名直传
        // 仅在面对部分严格的 OpenAI 兼容厂商时把 m4a 标成 mp4，安全的兜底是 wav
        String compatFormat = fmt;

        Map<String, Object> audioPart = Map.of(
                "type", "input_audio",
                "input_audio", Map.of(
                        "data", "data:audio/" + compatFormat + ";base64," + audioBase64,
                        "format", compatFormat
                )
        );
        Map<String, Object> textPart = Map.of("type", "text", "text",
                "Transcribe the audio verbatim. Return only the transcribed text, no commentary.");

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(textPart, audioPart)
                ))
        );

        Map response;
        try {
            AgentDebugLog.ndjson("A1", "LlmService.transcribeAudioOpenAICompat",
                    "calling STT", "{\"model\":\"" + model + "\",\"format\":\"" + compatFormat + "\"}");
            response = webClientBuilder.baseUrl(url)
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(25 * 1024 * 1024))
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException wcre) {
            AgentDebugLog.ndjson("A1err", "LlmService.transcribeAudioOpenAICompat",
                    "WebClientResponseException",
                    "{\"status\":" + wcre.getStatusCode().value() + "}");
            throw wcre;
        } catch (Exception ex) {
            AgentDebugLog.ndjson("A1err", "LlmService.transcribeAudioOpenAICompat",
                    ex.getClass().getSimpleName(), "{}");
            throw ex;
        }

        if (response == null) throw new RuntimeException("STT 返回为空");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("STT 未返回 choices");
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) throw new RuntimeException("STT 返回 message 为空");
        Object content = message.get("content");
        return content == null ? "" : content.toString().trim();
    }

    /**
     * 文本转语音（TTS）。根据 tts stage 配置的 URL 自动选路：
     *   · dashscope.aliyuncs.com → CosyVoice WebSocket SDK
     *   · generativelanguage.googleapis.com → Gemini Native TTS
     *   · 其它 → 默认尝试 Gemini Native（兜底）
     *
     * 返回纯 base64（不含 data: 前缀），mime 类型由对应 service 决定。
     */
    public String synthesizeSpeech(String text, String voiceName, Long userId) {
        String url = systemApiConfigService.getApiUrl(SystemApiConfigService.STAGE_TTS);
        if (url != null && url.contains("dashscope.aliyuncs.com")) {
            return cosyVoiceService.synthesizeSpeech(text, voiceName);
        }
        return geminiNativeService.synthesizeSpeech(text, voiceName, userId);
    }

    /** 当前 TTS 输出的 mime — 由具体路由决定 */
    public String getTtsMimeType() {
        String url = systemApiConfigService.getApiUrl(SystemApiConfigService.STAGE_TTS);
        if (url != null && url.contains("dashscope.aliyuncs.com")) {
            return CosyVoiceService.mimeType();
        }
        return "audio/wav";
    }
}