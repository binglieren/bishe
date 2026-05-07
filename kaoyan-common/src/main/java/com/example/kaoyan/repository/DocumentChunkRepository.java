package com.example.kaoyan.repository;

import com.example.kaoyan.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);

    /**
     * 向量相似度检索（按知识库过滤）：
     * 只检索 document.knowledge_base_id = :kbId 且 document.enabled = TRUE 的切片
     */
    @Query(value = "SELECT dc.* FROM document_chunk dc " +
            "JOIN document d ON dc.document_id = d.id " +
            "WHERE d.user_id = :userId " +
            "  AND d.knowledge_base_id = :kbId " +
            "  AND d.enabled = TRUE " +
            "ORDER BY dc.embedding <=> CAST(:queryVector AS vector) " +
            "LIMIT :limit",
            nativeQuery = true)
    List<DocumentChunk> findSimilarChunksInKb(Long userId, Long kbId, String queryVector, int limit);

    /** 向量相似度检索（不限知识库，仅限 enabled 文档） */
    @Query(value = "SELECT dc.* FROM document_chunk dc " +
            "JOIN document d ON dc.document_id = d.id " +
            "WHERE d.enabled = TRUE " +
            "ORDER BY dc.embedding <=> CAST(:queryVector AS vector) " +
            "LIMIT :limit",
            nativeQuery = true)
    List<DocumentChunk> findSimilarChunks(String queryVector, int limit);

    /** 向量相似度检索（按 kbId，不限 userId） */
    @Query(value = "SELECT dc.* FROM document_chunk dc " +
            "JOIN document d ON dc.document_id = d.id " +
            "WHERE d.knowledge_base_id = :kbId AND d.enabled = TRUE " +
            "ORDER BY dc.embedding <=> CAST(:queryVector AS vector) " +
            "LIMIT :limit",
            nativeQuery = true)
    List<DocumentChunk> findSimilarChunksByKbId(Long kbId, String queryVector, int limit);
}
