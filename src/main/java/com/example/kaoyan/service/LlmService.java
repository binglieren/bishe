package com.example.kaoyan.service;

import com.example.kaoyan.entity.AiConfig;
import com.example.kaoyan.repository.AiConfigRepository;
import com.example.kaoyan.util.AgentDebugLog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmService {

    private final WebClient.Builder webClientBuilder;
    private final AiConfigRepository aiConfigRepository;

    @Value("${llm.api-key}")
    private String defaultApiKey;

    @Value("${llm.api-url}")
    private String defaultApiUrl;

    @Value("${llm.model}")
    private String defaultModel;

    @Value("${llm.embedding-model:}")
    private String defaultEmbeddingModel;

    private AiConfig getUserConfig(Long userId) {
        if (userId == null) return null;
        return aiConfigRepository.findByUserId(userId).orElse(null);
    }

    private String resolveApiKey(Long userId) {
        AiConfig config = getUserConfig(userId);
        if (config != null && config.getApiKey() != null && !config.getApiKey().isBlank()) {
            return config.getApiKey();
        }
        return defaultApiKey;
    }

    private String normalizeApiUrl(String url) {
        if (url == null) return null;
        url = url.trim();
        // Strip trailing slash and common endpoint suffixes so base URL is clean
        if (url.endsWith("/chat/completions")) url = url.substring(0, url.length() - "/chat/completions".length());
        if (url.endsWith("/completions"))      url = url.substring(0, url.length() - "/completions".length());
        if (url.endsWith("/"))                 url = url.substring(0, url.length() - 1);
        return url;
    }

    private String resolveApiUrl(Long userId) {
        AiConfig config = getUserConfig(userId);
        if (config != null && config.getApiUrl() != null && !config.getApiUrl().isBlank()) {
            return normalizeApiUrl(config.getApiUrl());
        }
        return normalizeApiUrl(defaultApiUrl);
    }

    private String resolveChatModel(Long userId) {
        AiConfig config = getUserConfig(userId);
        if (config != null && config.getChatModel() != null && !config.getChatModel().isBlank()) {
            return config.getChatModel();
        }
        return defaultModel;
    }

    private String resolveEmbeddingModel(Long userId) {
        AiConfig config = getUserConfig(userId);
        if (config != null && config.getEmbeddingModel() != null && !config.getEmbeddingModel().isBlank()) {
            return config.getEmbeddingModel();
        }
        return defaultEmbeddingModel;
    }

    private Double resolveTemperature(Long userId) {
        AiConfig config = getUserConfig(userId);
        if (config != null && config.getTemperature() != null) {
            return config.getTemperature();
        }
        return 0.7;
    }

    private Integer resolveMaxTokens(Long userId) {
        AiConfig config = getUserConfig(userId);
        if (config != null && config.getMaxTokens() != null) {
            return config.getMaxTokens();
        }
        return 2000;
    }

    public String resolveSystemPrompt(Long userId) {
        AiConfig config = getUserConfig(userId);
        if (config != null && config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            return config.getSystemPrompt();
        }
        return "你是一个专业的考研辅导助手。请根据提供的参考资料回答用户的问题。" +
                "如果参考资料中包含相关信息，请基于资料回答并说明来源。" +
                "如果参考资料不包含相关信息，请基于你的知识回答，并告知用户这不是来自其上传的资料。";
    }

    public float[] getEmbedding(String text) {
        return getEmbedding(text, null);
    }

    public float[] getEmbedding(String text, Long userId) {
        String url = resolveApiUrl(userId);
        String key = resolveApiKey(userId);
        String model = resolveEmbeddingModel(userId);

        WebClient client = webClientBuilder.baseUrl(url).build();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", text
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

        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.get(i).floatValue();
        }
        return result;
    }

    public String chat(List<Map<String, String>> messages) {
        return chat(messages, null);
    }

    public String chat(List<Map<String, String>> messages, Long userId) {
        String url = resolveApiUrl(userId);
        String key = resolveApiKey(userId);
        String model = resolveChatModel(userId);
        Double temperature = resolveTemperature(userId);
        Integer maxTokens = resolveMaxTokens(userId);

        WebClient client = webClientBuilder.baseUrl(url).build();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "temperature", temperature,
                "max_tokens", maxTokens
        );

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

    public String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}