package com.bizkredit.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    private Long userId;

    @Column(nullable = false)
    private String action;

    private String entityType;

    private String recordId;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}

