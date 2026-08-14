package com.bizkredit.auth.dto;

import com.bizkredit.auth.enums.Role;

public record AuthResponse(
        String token,
        String tokenType,
        Long userId,
        String name,
        String email,
        Role role
) {}

