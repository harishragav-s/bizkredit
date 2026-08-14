package com.bizkredit.auth.service;

import com.bizkredit.auth.entity.AuditLog;
import com.bizkredit.auth.repository.AuditLogRepository;
import com.bizkredit.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void log(Long userId, String action, String entityType, String recordId) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .entityType(entityType)
                    .recordId(recordId)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getLogsFiltered(Long userId, String username, String entityType, String action,
                                           java.time.LocalDateTime from, java.time.LocalDateTime to,
                                           int page, int size) {
        List<Long> userIds = null;

        if (username != null && !username.isBlank()) {
            userIds = userRepository
                    .findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(username, username)
                    .stream()
                    .map(u -> u.getUserId())
                    .toList();

            if (userIds.isEmpty()) {
                return Page.empty(PageRequest.of(page, size));
            }
        } else if (userId != null) {
            userIds = List.of(userId);
        }

        return auditLogRepository.findWithFilters(userIds, entityType, action, from, to,
                PageRequest.of(page, size));
    }
}

