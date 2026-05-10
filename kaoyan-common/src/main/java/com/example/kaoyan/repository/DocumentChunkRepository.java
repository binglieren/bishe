package com.example.kaoyan.repository;

import com.example.kaoyan.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);

    /** 原生插入，显式 CAST embedding 字符串为 vector 类型 */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO document_chunk (document_id, content, chunk_index, embedding, created_at) " +
            "VALUES (:documentId, :content, :chunkIndex, CAST(:embedding AS vector(1536)), NOW())",
            nativeQuery = true)
    void insertChunk(@Param("documentId") Long documentId,
                     @Param("content") String content,
                     @Param("chunkIndex") Integer chunkIndex,
                     @Param("embedding") String embedding);

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

    /** 多知识库联合向量检索 — 在多个指定知识库中按相似度排序 */
    @Query(value = "SELECT dc.*, d.original_filename, d.knowledge_base_id, " +
            "1 - (dc.embedding <=> CAST(:queryVector AS vector)) AS _score " +
            "FROM document_chunk dc " +
            "JOIN document d ON dc.document_id = d.id " +
            "WHERE d.knowledge_base_id = ANY(CAST(:kbIds AS bigint[])) " +
            "AND d.enabled = TRUE " +
            "ORDER BY dc.embedding <=> CAST(:queryVector AS vector) " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findSimilarChunksInKbs(@org.springframework.data.repository.query.Param("kbIds") String kbIds,
                                           @org.springframework.data.repository.query.Param("queryVector") String queryVector,
                                           @org.springframework.data.repository.query.Param("limit") int limit);
}
