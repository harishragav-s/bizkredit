package com.bizkredit.monitoring.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Authenticates outbound Feign calls from monitoring-service.
 *
 * PREVIOUSLY this forwarded the caller's own JWT for everything except the
 * two ADMIN-only writes (npa-status, drawdown overdue). That left every
 * other outbound call - including ones backing the @Scheduled EWS/NPA jobs,
 * which have no request context at all - dependent on there being a live,
 * unexpired user token in scope. Same failure mode as credit-service and
 * collateral-service: a stale token turns an authorised action into a
 * silently swallowed downstream failure.
 *
 * FIX: always use a short-lived system token. Every call made through this
 * service's Feign clients is monitoring-service's own internal follow-
 * through on work already authorised by its own @PreAuthorize checks (or a
 * scheduled job with no caller at all), not a proxy of the browser's
 * request - and every downstream endpoint reached this way already allows
 * ADMIN, so this changes no authorization outcomes.
 */
@Configuration
public class FeignClientConfig {

    @Autowired
    private JwtUtil jwtUtil;

    @Bean
    public RequestInterceptor authForwardingInterceptor() {
        return template -> {
            String systemToken = jwtUtil.generateSystemToken("system-monitoring-service", "ADMIN");
            template.header("Authorization", "Bearer " + systemToken);
        };
    }

}
