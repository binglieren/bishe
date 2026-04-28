package com.example.kaoyan.repository;

import com.example.kaoyan.entity.QuestionKnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface QuestionKnowledgePointRepository
        extends JpaRepository<QuestionKnowledgePoint, QuestionKnowledgePoint.PK> {

    List<QuestionKnowledgePoint> findByQuestionId(Long questionId);

    List<QuestionKnowledgePoint> findByQuestionIdIn(Collection<Long> questionIds);

    List<QuestionKnowledgePoint> findByKnowledgePointId(Long knowledgePointId);

    @Modifying
    @Transactional
    @Query("DELETE FROM QuestionKnowledgePoint q WHERE q.questionId = :questionId")
    void deleteByQuestionId(@Param("questionId") Long questionId);

    /**
     * 按知识点集合查询题目 id（用于基于标签的候选召回）。
     */
    @Query("SELECT DISTINCT q.questionId FROM QuestionKnowledgePoint q " +
           "WHERE q.knowledgePointId IN :kpIds")
    List<Long> findQuestionIdsByKnowledgePointIdIn(@Param("kpIds") Collection<Long> kpIds);

    /**
     * 每个知识点关联的题目数（用于图谱节点大小）。
     * 返回 [knowledge_point_id, count] 的 Object[] 行。
     */
    @Query(value = "SELECT qkp.knowledge_point_id, COUNT(DISTINCT qkp.question_id) " +
                   "FROM question_knowledge_point qkp " +
                   "JOIN knowledge_point kp ON kp.id = qkp.knowledge_point_id " +
                   "WHERE (:subject IS NULL OR kp.subject = :subject) " +
                   "GROUP BY qkp.knowledge_point_id",
           nativeQuery = true)
    List<Object[]> countQuestionsPerKp(@Param("subject") String subject);

    /**
     * 共现关系：同一题里出现的两个 KP 互相连一条边。
     * 返回 [src_kp_id, dst_kp_id, count]，且 src_kp_id < dst_kp_id 避免重复。
     * HAVING count >= 2 过滤噪声。
     */
    @Query(value = "SELECT a.knowledge_point_id AS src, " +
                   "       b.knowledge_point_id AS dst, " +
                   "       COUNT(*)             AS w " +
                   "FROM question_knowledge_point a " +
                   "JOIN question_knowledge_point b " +
                   "  ON a.question_id = b.question_id " +
                   " AND a.knowledge_point_id < b.knowledge_point_id " +
                   "JOIN knowledge_point kp_a ON kp_a.id = a.knowledge_point_id " +
                   "JOIN knowledge_point kp_b ON kp_b.id = b.knowledge_point_id " +
                   "WHERE (:subject IS NULL " +
                   "       OR (kp_a.subject = :subject AND kp_b.subject = :subject)) " +
                   "GROUP BY src, dst " +
                   "HAVING COUNT(*) >= 2",
           nativeQuery = true)
    List<Object[]> findCoOccurrenceEdges(@Param("subject") String subject);

    /**
     * 取与指定 KP 共现最多的 top N 个 KP（用于详情抽屉的"相关知识点"）。
     */
    @Query(value = "SELECT other_kp_id, total FROM ( " +
                   "  SELECT b.knowledge_point_id AS other_kp_id, COUNT(*) AS total " +
                   "  FROM question_knowledge_point a " +
                   "  JOIN question_knowledge_point b ON a.question_id = b.question_id " +
                   "  WHERE a.knowledge_point_id = :kpId " +
                   "    AND b.knowledge_point_id <> :kpId " +
                   "  GROUP BY b.knowledge_point_id " +
                   "  ORDER BY total DESC " +
                   "  LIMIT :limit " +
                   ") t",
           nativeQuery = true)
    List<Object[]> findTopRelatedKps(@Param("kpId") Long kpId, @Param("limit") int limit);
}
