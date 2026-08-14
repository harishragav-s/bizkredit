package com.bizkredit.credit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

// Minimal WRITE-ONLY mapping of the shared `notification` table, which
// is owned by monitoring-service. This service only ever creates
// notification rows (it never reads them back - that's the frontend's
// job, via monitoring-service's GET endpoint), so this deliberately
// maps user_id as a plain Long and category/status as plain Strings,
// rather than mirroring the full User entity or coupling to
// monitoring-service's enums. The String values written here
// ("APPLICATION", "UNREAD", etc.) are exactly what monitoring-service's
// @Enumerated(EnumType.STRING) columns expect, so they round-trip
// correctly when monitoring-service reads them back as real enums.
@Entity
@Table(name = "notification", schema = "bizkredit_monitoring_db")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private String message;

    private String category;

    private String status;

    private LocalDate createdDate;
}
