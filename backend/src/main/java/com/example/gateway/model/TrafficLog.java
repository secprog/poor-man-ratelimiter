package com.example.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("traffic_logs")
public class TrafficLog {
    @Id
    private UUID id;
    private LocalDateTime timestamp;
    private String path;
    private String clientIp;
    private int statusCode;
    private boolean allowed;
}
