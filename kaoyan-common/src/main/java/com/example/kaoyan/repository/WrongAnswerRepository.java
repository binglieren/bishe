package com.example.kaoyan.repository;

import com.example.kaoyan.entity.WrongAnswerRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WrongAnswerRepository extends JpaRepository<WrongAnswerRecord, Long> {

    Page<WrongAnswerRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<WrongAnswerRecord> findByUserIdAndIsResolvedOrderByCreatedAtDesc(Long userId, Boolean isResolved, Pageable pageable);

    boolean existsByUserIdAndQuestionId(Long userId, Long questionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM WrongAnswerRecord w WHERE w.questionId = :questionId")
    void deleteByQuestionId(@Param("questionId") Long questionId);

    /**
     * 找用户在某个知识点下答错过的 question_id 列表（去重，最近 N 个）。
     * 用于知识图谱节点详情抽屉。
     */
    @org.springframework.data.jpa.repository.Query(value =
            "SELECT war.question_id " +
            "FROM wrong_answer_record war " +
            "JOIN question_knowledge_point qkp ON qkp.question_id = war.question_id " +
            "WHERE war.user_id = :userId AND qkp.knowledge_point_id = :kpId " +
            "GROUP BY war.question_id " +
            "ORDER BY MAX(war.created_at) DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    java.util.List<Long> findWrongQuestionIdsByUserAndKp(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("kpId") Long kpId,
            @org.springframework.data.repository.query.Param("limit") int limit);
}
