package com.bizkredit.auth.controller;

import com.bizkredit.auth.dto.ApiResponse;
import com.bizkredit.auth.entity.User;
import com.bizkredit.auth.enums.Role;
import com.bizkredit.auth.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Auth, Users, Scope & Audit")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RELATIONSHIP_MANAGER','CREDIT_ANALYST','UNDERWRITING_MANAGER','COLLATERAL_EVALUATOR','SME_APPLICANT')")
    public ResponseEntity<ApiResponse<User>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("User fetched", userService.getUserById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RELATIONSHIP_MANAGER')")
    public ResponseEntity<ApiResponse<List<User>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("All users", userService.getAllUsers()));
    }

    @GetMapping("/role/{role}")
    @PreAuthorize("hasAnyRole('ADMIN','RELATIONSHIP_MANAGER')")
    public ResponseEntity<ApiResponse<List<User>>> getByRole(@PathVariable Role role) {
        return ResponseEntity.ok(ApiResponse.ok("Users fetched", userService.getUsersByRole(role)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<User>> updateStatus(
            @PathVariable Long id,
            @RequestParam String value) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", userService.updateStatus(id, value)));
    }
}

