package com.example.kaoyan.repository;

import com.example.kaoyan.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    List<KnowledgeBase> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<KnowledgeBase> findFirstByUserIdOrderByCreatedAtAsc(Long userId);

    long countByUserId(Long userId);
}
