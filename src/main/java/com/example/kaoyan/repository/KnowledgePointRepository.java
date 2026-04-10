package com.example.kaoyan.repository;

import com.example.kaoyan.entity.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {

    List<KnowledgePoint> findBySubject(String subject);

    List<KnowledgePoint> findByParentId(Long parentId);

    List<KnowledgePoint> findByParentIdIsNull();
}
