package com.bizkredit.auth;

import com.bizkredit.auth.config.JwtUtil;
import com.bizkredit.auth.dto.LoginRequest;
import com.bizkredit.auth.dto.RegisterRequest;
import com.bizkredit.auth.entity.User;
import com.bizkredit.auth.repository.AuditLogRepository;
import com.bizkredit.auth.repository.UserRepository;
import com.bizkredit.auth.service.AuthService;
import com.bizkredit.auth.service.CustomUserDetailsService;
import com.bizkredit.auth.enums.Role;
import com.bizkredit.auth.exception.BadRequestException;
import com.bizkredit.auth.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private CustomUserDetailsService customUserDetailsService;
    @Mock private UserDetails userDetails;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .userId(1L)
                .name("Harish Kumar")
                .email("harish@bizkredit.com")
                .password("hashedPassword")
                .phone("9876543210")
                .role(Role.ADMIN)
                .branchId("BR001")
                .status("Active")
                .failedLoginAttempts(0)
                .build();
    }

    @Test
    void register_success_doesNotIssueToken() {
        RegisterRequest request = new RegisterRequest(
                "Harish Kumar", "harish@bizkredit.com", "password123",
                "9876543210", Role.ADMIN, "BR001");

        when(userRepository.existsByEmail("harish@bizkredit.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        var response = authService.register(request);

        assertThat(response.token()).isNull();
        assertThat(response.tokenType()).isNull();
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.role()).isEqualTo(Role.ADMIN);

        verify(userRepository, times(1)).save(any(User.class));
        verify(auditLogRepository, times(1)).save(any());
        verify(customUserDetailsService, never()).loadUserByUsername(any());
        verify(jwtUtil, never()).generateToken(any(UserDetails.class), anyMap());
    }

    @Test
    void register_duplicateEmail_throwsBadRequest() {
        RegisterRequest request = new RegisterRequest(
                "Harish Kumar", "harish@bizkredit.com", "password123",
                "9876543210", Role.ADMIN, "BR001");

        when(userRepository.existsByEmail("harish@bizkredit.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_success_returnsToken() {
        LoginRequest request = new LoginRequest("harish@bizkredit.com", "password123");

        when(userRepository.findByEmail("harish@bizkredit.com")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(customUserDetailsService.loadUserByUsername("harish@bizkredit.com")).thenReturn(userDetails);
        when(jwtUtil.generateToken(any(UserDetails.class), anyMap())).thenReturn("jwt-token-123");

        var response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token-123");
        assertThat(response.email()).isEqualTo("harish@bizkredit.com");

        verify(authenticationManager, times(1)).authenticate(any());
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    void login_lockedAccount_throwsForbiddenException() {
        sampleUser.setStatus("Locked");
        LoginRequest request = new LoginRequest("harish@bizkredit.com", "password123");

        when(userRepository.findByEmail("harish@bizkredit.com")).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Locked");

        verify(jwtUtil, never()).generateToken(any(UserDetails.class), anyMap());
    }
}

