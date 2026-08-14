package com.bizkredit.collateral.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Local audit logging for this microservice.
// The canonical audit_log table lives in auth-service; in the shared-database,
// no-cross-service-calls architecture used here, each downstream service logs
// its own actions via SLF4J instead of writing cross-service to auth-service's table.
@Slf4j
@Service
public class AuditLogService {

    public void log(Long userId, String action, String entityType, String recordId) {
        log.info("AUDIT [collateral-service] userId={} action={} entityType={} recordId={}",
                userId, action, entityType, recordId);
    }
}
