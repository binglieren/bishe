package com.example.kaoyan.repository;

import com.example.kaoyan.entity.KnowledgePointFocus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface KnowledgePointFocusRepository
        extends JpaRepository<KnowledgePointFocus, KnowledgePointFocus.PK> {

    @Query("SELECT f.knowledgePointId FROM KnowledgePointFocus f WHERE f.userId = :userId")
    List<Long> findKpIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM KnowledgePointFocus f " +
           "WHERE f.userId = :userId AND f.knowledgePointId = :kpId")
    void deleteByUserIdAndKpId(@Param("userId") Long userId, @Param("kpId") Long kpId);
}
