package com.example.kaoyan.repository;

import com.example.kaoyan.entity.KnowledgeMastery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface KnowledgeMasteryRepository extends JpaRepository<KnowledgeMastery, Long> {

    Optional<KnowledgeMastery> findByUserIdAndKnowledgePointId(Long userId, Long knowledgePointId);

    List<KnowledgeMastery> findByUserIdOrderByMasteryLevelAsc(Long userId);

    @Query("SELECT km FROM KnowledgeMastery km WHERE km.userId = :userId AND km.masteryLevel < :threshold ORDER BY km.masteryLevel ASC")
    List<KnowledgeMastery> findWeakPoints(Long userId, java.math.BigDecimal threshold);

    List<KnowledgeMastery> findByUserId(Long userId);

    /** 删除知识点时清理掌握度记录 */
    @Modifying
    @Transactional
    @Query("DELETE FROM KnowledgeMastery km WHERE km.knowledgePointId = :kpId")
    void deleteByKnowledgePointId(@Param("kpId") Long kpId);
}
