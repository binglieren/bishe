package com.example.kaoyan.rag;

import com.example.kaoyan.entity.Document;
import com.example.kaoyan.entity.DocumentChunk;
import com.example.kaoyan.entity.KnowledgeBase;
import com.example.kaoyan.repository.DocumentChunkRepository;
import com.example.kaoyan.repository.DocumentRepository;
import com.example.kaoyan.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG MCP Server — 3 个工具：
 *   1. search_knowledge_base  语义检索文档片段
 *   2. list_knowledge_bases   列出用户知识库
 *   3. get_document_info      文档详情
 */
@RestController
@RequestMapping("/mcp/rag")
@RequiredArgsConstructor
public class RagMcpTools {

    private final DocumentChunkRepository chunkRepo;
    private final DocumentRepository docRepo;
    private final KnowledgeBaseRepository kbRepo;
    private final WebClient.Builder webClientBuilder;

    @Value("${llm.embedding.api-url}")
    private String embeddingApiUrl;

    @Value("${llm.embedding.api-key}")
    private String embeddingApiKey;

    @Value("${llm.embedding.model}")
    private String embeddingModel;

    /** 1. 语义检索文档片段（向量 top-10 → 关键词重排序 → top-5） */
    @PostMapping("/search")
    public List<Map<String, Object>> searchKnowledgeBase(@RequestBody Map<String, Object> req) {
        String query = (String) req.getOrDefault("query", "");
        Number kbIdNum = (Number) req.get("kbId");
        Long kbId = kbIdNum != null ? kbIdNum.longValue() : null;

        if (query == null || query.isBlank()) return List.of();

        // 向量化查询
        float[] queryVec = getEmbedding(query);
        if (queryVec == null) return List.of();

        String vecStr = vectorToString(queryVec);

        // pgvector 余弦检索 top-15（给重排序留足够候选）
        List<DocumentChunk> chunks;
        if (kbId != null) {
            chunks = chunkRepo.findSimilarChunksByKbId(kbId, vecStr, 15);
        } else {
            chunks = chunkRepo.findSimilarChunks(vecStr, 15);
        }

        if (chunks.isEmpty()) return List.of();

        // ── 重排序：向量相似度 + 关键词重叠 ──
        String[] queryWords = query.toLowerCase().split("[\\s，,。！？；：\"'（）\\[\\]《》\\-]+");

        List<Map<String, Object>> scored = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            Document doc = docRepo.findById(chunk.getDocumentId()).orElse(null);

            // 向量分（位置越靠前分越高，归一化到 0~1）
            double vectorScore = 1.0 - (double) i / chunks.size();

            // 关键词重叠分
            String content = chunk.getContent() != null ? chunk.getContent().toLowerCase() : "";
            int matchCount = 0;
            for (String w : queryWords) {
                if (w.length() >= 2 && content.contains(w)) matchCount++;
            }
            double keywordScore = queryWords.length > 0
                ? (double) matchCount / queryWords.length
                : 0;

            // 组合分（向量占 60%，关键词占 40%）
            double combinedScore = vectorScore * 0.6 + keywordScore * 0.4;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("content", chunk.getContent());
            item.put("docId", chunk.getDocumentId());
            item.put("docName", doc != null ? doc.getOriginalFilename() : "未知文档");
            item.put("chunkIndex", chunk.getChunkIndex());
            item.put("score", Math.round(combinedScore * 100.0) / 100.0);
            scored.add(item);
        }

        // 按组合分降序，取 top-5
        scored.sort((a, b) -> Double.compare(
            (Double) b.get("score"), (Double) a.get("score")));

        return scored.size() > 5 ? scored.subList(0, 5) : scored;
    }

    /** 2. 列出用户知识库 */
    @PostMapping("/list-kb")
    public List<Map<String, Object>> listKnowledgeBases(@RequestBody Map<String, Object> req) {
        Long userId = toLong(req.get("userId"));
        if (userId == null) return List.of();

        return kbRepo.findByUserIdOrderByCreatedAtAsc(userId).stream().map(kb -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", kb.getId());
            item.put("name", kb.getName());
            item.put("description", kb.getDescription());
            return item;
        }).collect(Collectors.toList());
    }

    /** 3. 文档详情 */
    @PostMapping("/doc-info")
    public Map<String, Object> getDocumentInfo(@RequestBody Map<String, Object> req) {
        Long docId = toLong(req.get("docId"));
        if (docId == null) return Map.of("error", "docId required");

        Document doc = docRepo.findById(docId).orElse(null);
        if (doc == null) return Map.of("error", "文档不存在");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", doc.getId());
        info.put("name", doc.getOriginalFilename());
        info.put("fileSize", doc.getFileSize());
        info.put("type", doc.getFileType());
        info.put("status", doc.getStatus());
        info.put("enabled", doc.getEnabled());
        return info;
    }

    // ─── Embedding 工具 ───

    private float[] getEmbedding(String text) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = Map.of(
            "model", embeddingModel,
            "input", text,
            "dimensions", 1536
        );
        try {
            Map response = webClientBuilder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .baseUrl(embeddingApiUrl).build()
                .post().uri("/embeddings")
                .header("Authorization", "Bearer " + embeddingApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            if (response == null) return null;
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            List<Double> embedding = (List<Double>) data.get(0).get("embedding");
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) result[i] = embedding.get(i).floatValue();
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private String vectorToString(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) {}
        }
        return null;
    }
}
