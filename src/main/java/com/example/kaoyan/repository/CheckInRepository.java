package com.example.kaoyan.repository;

import com.example.kaoyan.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    Optional<CheckIn> findByUserIdAndCheckDate(Long userId, LocalDate checkDate);

    List<CheckIn> findByUserIdOrderByCheckDateDesc(Long userId);

    @Query("SELECT COUNT(c) FROM CheckIn c WHERE c.userId = :userId")
    Integer countByUserId(Long userId);

    @Query("SELECT COALESCE(SUM(c.studyMinutes), 0) FROM CheckIn c WHERE c.userId = :userId")
    Integer sumStudyMinutesByUserId(Long userId);

    List<CheckIn> findByUserIdAndCheckDateBetween(Long userId, LocalDate start, LocalDate end);
}
