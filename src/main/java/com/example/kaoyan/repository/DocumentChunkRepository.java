package com.example.kaoyan.repository;

import com.example.kaoyan.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);

    /**
     * 向量相似度检索：基于 pgvector 的余弦相似度搜索
     * 返回与查询向量最相似的文档分块
     */
    @Query(value = "SELECT dc.* FROM document_chunk dc " +
            "JOIN document d ON dc.document_id = d.id " +
            "WHERE d.user_id = :userId " +
            "ORDER BY dc.embedding <=> CAST(:queryVector AS vector) " +
            "LIMIT :limit",
            nativeQuery = true)
    List<DocumentChunk> findSimilarChunks(Long userId, String queryVector, int limit);
}
