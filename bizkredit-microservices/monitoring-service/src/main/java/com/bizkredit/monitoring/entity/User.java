package com.bizkredit.monitoring.entity;

import com.bizkredit.monitoring.enums.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

// Local mapping of the users table, owned and written by auth-service.
// monitoring-service reads this to attach notifications to a user - it
// never creates new users.
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    private String password;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String branchId;

    @Builder.Default
    private String status = "Active";

    @Builder.Default
    private Integer failedLoginAttempts = 0;

    private String region;
}
