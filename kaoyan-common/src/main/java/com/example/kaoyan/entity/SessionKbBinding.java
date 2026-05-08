package com.example.kaoyan.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "session_kb_binding")
@IdClass(SessionKbBindingId.class)
public class SessionKbBinding {

    @Id
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Id
    @Column(name = "kb_id", nullable = false)
    private Long kbId;

    @Column(name = "weight")
    private Double weight = 1.0;
}
