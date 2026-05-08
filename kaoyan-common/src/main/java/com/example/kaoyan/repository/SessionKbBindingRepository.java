package com.example.kaoyan.repository;

import com.example.kaoyan.entity.SessionKbBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SessionKbBindingRepository extends JpaRepository<SessionKbBinding, Long> {

    List<SessionKbBinding> findBySessionId(Long sessionId);

    List<SessionKbBinding> findByKbId(Long kbId);

    @Modifying
    @Transactional
    @Query("DELETE FROM SessionKbBinding b WHERE b.sessionId = :sessionId")
    void deleteBySessionId(Long sessionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM SessionKbBinding b WHERE b.sessionId = :sessionId AND b.kbId = :kbId")
    void deleteBySessionIdAndKbId(Long sessionId, Long kbId);
}
