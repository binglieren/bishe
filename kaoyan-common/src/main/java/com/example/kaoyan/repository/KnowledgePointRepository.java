package com.example.kaoyan.repository;

import com.example.kaoyan.entity.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {

    List<KnowledgePoint> findBySubject(String subject);

    List<KnowledgePoint> findByParentId(Long parentId);

    List<KnowledgePoint> findByParentIdIsNull();

    Optional<KnowledgePoint> findByNameAndSubject(String name, String subject);

    boolean existsBySubject(String subject);

    @Query("SELECT kp FROM KnowledgePoint kp WHERE kp.id IN " +
           "(SELECT DISTINCT uq.question.knowledgePointId FROM UserQuestion uq " +
           "WHERE uq.userId = :userId AND uq.question.knowledgePointId IS NOT NULL)")
    List<KnowledgePoint> findKnowledgePointsByUserId(@Param("userId") Long userId);

    /** 删除知识点时解除子知识点的父引用 */
    @Modifying
    @Transactional
    @Query("UPDATE KnowledgePoint kp SET kp.parentId = NULL WHERE kp.parentId = :parentId")
    void clearParentIdByParentId(@Param("parentId") Long parentId);
}
