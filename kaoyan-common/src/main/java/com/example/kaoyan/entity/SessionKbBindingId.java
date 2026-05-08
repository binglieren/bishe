package com.example.kaoyan.entity;

import lombok.Data;
import java.io.Serializable;

@Data
public class SessionKbBindingId implements Serializable {
    private Long sessionId;
    private Long kbId;
}
