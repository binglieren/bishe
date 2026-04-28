package com.example.kaoyan.repository;

import com.example.kaoyan.entity.SystemApiConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemApiConfigRepository extends JpaRepository<SystemApiConfig, String> {
}
