package com.example.kaoyan.repository;

import com.example.kaoyan.entity.AiConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiConfigRepository extends JpaRepository<AiConfig, Long> {
    Optional<AiConfig> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}