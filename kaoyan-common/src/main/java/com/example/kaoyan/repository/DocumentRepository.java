package com.example.kaoyan.repository;

import com.example.kaoyan.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByUserIdOrderByUploadTimeDesc(Long userId);

    List<Document> findByUserIdAndKnowledgeBaseIdOrderByUploadTimeDesc(Long userId, Long knowledgeBaseId);

    long countByKnowledgeBaseId(Long knowledgeBaseId);

    long countByKnowledgeBaseIdAndEnabledTrue(Long knowledgeBaseId);

    List<Document> findByUserIdAndKnowledgeBaseIdIsNull(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Document d SET d.knowledgeBaseId = :kbId WHERE d.userId = :userId AND d.knowledgeBaseId IS NULL")
    int migrateOrphansToKb(@Param("userId") Long userId, @Param("kbId") Long kbId);
}
