package com.bizkredit.auth.config;

import com.bizkredit.auth.entity.User;
import com.bizkredit.auth.enums.Role;
import com.bizkredit.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String DEFAULT_ADMIN_EMAIL = "admin@bizkredit.com";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@123";

    @Override
    public void run(String... args) {
        if (!userRepository.findByRole(Role.ADMIN).isEmpty()) {
            log.info("Admin account already exists - skipping bootstrap seed.");
            return;
        }

        log.info("No admin account found - seeding default admin ({})", DEFAULT_ADMIN_EMAIL);

        userRepository.save(User.builder()
                .name("Default Admin")
                .email(DEFAULT_ADMIN_EMAIL)
                .password(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD))
                .phone("0000000000")
                .role(Role.ADMIN)
                .branchId("HQ")
                .status("Active")
                .failedLoginAttempts(0)
                .build());

        log.warn("Default admin created - email: {}, password: {} " +
                        "- change this password after first login in any real deployment.",
                DEFAULT_ADMIN_EMAIL, DEFAULT_ADMIN_PASSWORD);
    }
}

