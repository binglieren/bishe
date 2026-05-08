package com.example.kaoyan.repository;

import com.example.kaoyan.entity.KnowledgePoint;
import com.example.kaoyan.entity.UserQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserQuestionRepository extends JpaRepository<UserQuestion, Long> {

    List<UserQuestion> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserQuestion> findByUserIdAndQuestionId(Long userId, Long questionId);

    boolean existsByUserIdAndQuestionId(Long userId, Long questionId);

    /**
     * 查询用户积累的所有知识点（去重）
     */
    @Query(value =
        "SELECT * FROM knowledge_point WHERE id IN (" +
        "SELECT DISTINCT q.knowledge_point_id FROM user_question uq " +
        "JOIN question q ON uq.question_id = q.id " +
        "WHERE uq.user_id = :userId)",
        nativeQuery = true)
    List<KnowledgePoint> findKnowledgePointsByUserId(@Param("userId") Long userId);
}
