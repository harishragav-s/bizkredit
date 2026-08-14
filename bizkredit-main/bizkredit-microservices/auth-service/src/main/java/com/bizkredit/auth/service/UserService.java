package com.bizkredit.auth.service;

import com.bizkredit.auth.entity.AuditLog;
import com.bizkredit.auth.entity.User;
import com.bizkredit.auth.enums.Role;
import com.bizkredit.auth.exception.BadRequestException;
import com.bizkredit.auth.exception.ResourceNotFoundException;
import com.bizkredit.auth.repository.AuditLogRepository;
import com.bizkredit.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> getUsersByRole(Role role) {
        return userRepository.findByRole(role);
    }

    @Transactional
    public User updateStatus(Long userId, String status) {
        User user = getUserById(userId);

        String validStatus = switch (status) {
            case "Active", "Locked", "Inactive" -> status;
            default -> throw new BadRequestException(
                    "Invalid status. Must be Active, Locked or Inactive");
        };

        user.setStatus(validStatus);
        User updated = userRepository.save(user);
        log.info("User {} status changed to {}", userId, status);

        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action("STATUS_UPDATE:" + status)
                .entityType("User")
                .recordId(String.valueOf(userId))
                .build());

        return updated;
    }
}

