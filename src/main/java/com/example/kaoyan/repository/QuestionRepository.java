package com.example.kaoyan.repository;

import com.example.kaoyan.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    Page<Question> findBySubject(String subject, Pageable pageable);

    Page<Question> findBySubjectAndType(String subject, String type, Pageable pageable);

    Page<Question> findBySubjectAndDifficulty(String subject, Integer difficulty, Pageable pageable);

    Page<Question> findByKnowledgePointId(Long knowledgePointId, Pageable pageable);

    @Query("SELECT q FROM Question q WHERE q.subject = :subject AND q.year = :year")
    Page<Question> findBySubjectAndYear(String subject, Integer year, Pageable pageable);

    @Query("SELECT q FROM Question q WHERE q.subject = :subject ORDER BY RANDOM()")
    List<Question> findRandomBySubject(String subject, Pageable pageable);

    @Query("SELECT q FROM Question q WHERE q.knowledgePointId = :knowledgePointId ORDER BY RANDOM()")
    List<Question> findRandomByKnowledgePointId(Long knowledgePointId, Pageable pageable);

    /** 更新题目向量嵌入（用于相似题推荐） */
    @Modifying
    @Transactional
    @Query(value = "UPDATE question SET embedding = CAST(:vectorStr AS vector) WHERE id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") Long id, @Param("vectorStr") String vectorStr);

    /** 基于向量相似度查找相似题目 */
    @Query(value =
        "SELECT q.* FROM question q " +
        "WHERE q.id != :excludeId AND q.embedding IS NOT NULL " +
        "ORDER BY q.embedding <=> CAST(:vectorStr AS vector) LIMIT :limit",
        nativeQuery = true)
    List<Question> findSimilarByVector(@Param("excludeId") Long excludeId,
                                       @Param("vectorStr") String vectorStr,
                                       @Param("limit") int limit);

    /** 按知识点查找相似题目（向量不可用时的降级方案） */
    List<Question> findByKnowledgePointIdAndIdNot(Long knowledgePointId, Long excludeId, Pageable pageable);

    /** 批量按知识点查询（用于推荐） */
    List<Question> findByKnowledgePointIdIn(Collection<Long> knowledgePointIds);

    /** 按科目随机取样（冷启动场景） */
    @Query(value = "SELECT * FROM question WHERE subject = :subject AND difficulty BETWEEN 2 AND 3 " +
                   "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Question> findColdStartBySubject(@Param("subject") String subject, @Param("limit") int limit);
}
