package com.bizkredit.auth.service;

import com.bizkredit.auth.config.JwtUtil;
import com.bizkredit.auth.dto.AuthResponse;
import com.bizkredit.auth.dto.LoginRequest;
import com.bizkredit.auth.dto.RegisterRequest;
import com.bizkredit.auth.entity.AuditLog;
import com.bizkredit.auth.entity.User;
import com.bizkredit.auth.enums.Role;
import com.bizkredit.auth.exception.BadRequestException;
import com.bizkredit.auth.exception.ForbiddenException;
import com.bizkredit.auth.repository.AuditLogRepository;
import com.bizkredit.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService customUserDetailsService;

    private static final int MAX_FAILED_ATTEMPTS = 5;

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .role(Role.SME_APPLICANT)
                .branchId(request.branchId())
                .status("Active")
                .failedLoginAttempts(0)
                .build();

        User saved = userRepository.save(user);

        saveAuditLog(saved.getUserId(), "REGISTER");
        log.info("User registered: {} [{}]", saved.getEmail(), saved.getRole());

        return buildRegistrationResponse(saved);
    }

    @Transactional
    public AuthResponse registerStaff(RegisterRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email already registered: " + request.email());
        }

        if (request.role() == null) {
            throw new BadRequestException("Role is required when creating a staff account");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .role(request.role())
                .branchId(request.branchId())
                .status("Active")
                .failedLoginAttempts(0)
                .build();

        User saved = userRepository.save(user);

        saveAuditLog(saved.getUserId(), "ADMIN_CREATE_STAFF");
        log.info("Staff account created by admin: {} [{}]", saved.getEmail(), saved.getRole());

        return buildRegistrationResponse(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );
        } catch (BadCredentialsException e) {
            handleFailedLogin(request.email());
            throw e;
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadRequestException("Invalid credentials"));

        if (!"Active".equals(user.getStatus())) {
            throw new ForbiddenException("Account is " + user.getStatus() + ". Contact admin.");
        }

        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        saveAuditLog(user.getUserId(), "LOGIN");
        log.info("User logged in: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(Long userId) {
        saveAuditLog(userId, "LOGOUT");
        log.info("User logged out: {}", userId);
    }

    private void handleFailedLogin(String email) {

        userRepository.findByEmail(email).ifPresent(user -> {

            int attempts = getFailedAttempts(user) + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setStatus("Locked");
                log.warn("Account locked after {} attempts: {}", attempts, user.getEmail());
            }

            userRepository.save(user);
            saveAuditLog(user.getUserId(), "LOGIN_FAILED");
        });
    }

    private int getFailedAttempts(User user) {
        return user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
    }

    private AuthResponse buildAuthResponse(User user) {

        String token = buildToken(user);

        return new AuthResponse(
                token,
                "Bearer",
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    private AuthResponse buildRegistrationResponse(User user) {

        return new AuthResponse(
                null,
                null,
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    private String buildToken(User user) {

        var userDetails = customUserDetailsService.loadUserByUsername(user.getEmail());

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("role", user.getRole().name());
        claims.put("branchId", user.getBranchId());

        return jwtUtil.generateToken(userDetails, claims);
    }

    private void saveAuditLog(Long userId, String action) {

        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action(action)
                .entityType("User")
                .recordId(String.valueOf(userId))
                .build());
    }
}

