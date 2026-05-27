package com.example.kaoyan.service;

import com.example.kaoyan.entity.Document;
import com.example.kaoyan.entity.KnowledgeBase;
import com.example.kaoyan.repository.DocumentRepository;
import com.example.kaoyan.repository.KnowledgeBaseRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final DocumentRepository docRepo;
    private final KnowledgeBaseRepository kbRepo;

    public List<Map<String, Object>> searchKnowledgeBase(String query, Long kbId) {
        if (query == null || query.isBlank()) return List.of();

        Embedding queryVec = embeddingModel.embed(query).content();
        if (queryVec == null) return List.of();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryVec)
                        .maxResults(20)
                        .build());

        List<EmbeddingMatch<TextSegment>> matches = result.matches().stream()
                .filter(m -> m.embedded() != null && m.embedded().metadata() != null)
                .filter(m -> "true".equals(m.embedded().metadata().getString("enabled")))
                .filter(m -> kbId == null || String.valueOf(kbId).equals(m.embedded().metadata().getString("kb_id")))
                .toList();

        if (matches.isEmpty()) return List.of();

        String[] queryWords = query.toLowerCase().split("[\\s，,。！？；：\"'（）\\[\\]《》\\-]+");

        List<Map<String, Object>> scored = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment seg = match.embedded();
            String docIdStr = seg.metadata().getString("document_id");

            double vectorScore = match.score();
            String content = seg.text().toLowerCase();
            int matchCount = 0;
            for (String w : queryWords) {
                if (w.length() >= 2 && content.contains(w)) matchCount++;
            }
            double keywordScore = queryWords.length > 0 ? (double) matchCount / queryWords.length : 0;
            double combinedScore = vectorScore * 0.6 + keywordScore * 0.4;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("content", seg.text());
            item.put("docId", docIdStr != null ? Long.valueOf(docIdStr) : null);
            item.put("docName", seg.metadata().getString("filename"));
            item.put("chunkIndex", seg.metadata().getString("chunk_index"));
            item.put("score", Math.round(combinedScore * 100.0) / 100.0);
            scored.add(item);
        }

        scored.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));
        return scored.size() > 5 ? scored.subList(0, 5) : scored;
    }

    public List<Map<String, Object>> listKnowledgeBases(Long userId) {
        if (userId == null) return List.of();

        return kbRepo.findByUserIdOrderByCreatedAtAsc(userId).stream().map(kb -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", kb.getId());
            item.put("name", kb.getName());
            item.put("description", kb.getDescription());
            return item;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getDocumentInfo(Long docId) {
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
}
