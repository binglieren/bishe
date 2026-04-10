package com.example.kaoyan.repository;

import com.example.kaoyan.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByUserIdOrderByUploadTimeDesc(Long userId);
}
