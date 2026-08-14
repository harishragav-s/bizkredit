package com.bizkredit.monitoring.entity;

import com.bizkredit.monitoring.enums.NotificationCategory;
import com.bizkredit.monitoring.enums.NotificationStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "notification")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    // Owned by auth-service - id only, validated over Feign (AuthServiceClient)
    // before a notification is attached to it.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.UNREAD;

    @Builder.Default
    private LocalDate createdDate = LocalDate.now();
}
