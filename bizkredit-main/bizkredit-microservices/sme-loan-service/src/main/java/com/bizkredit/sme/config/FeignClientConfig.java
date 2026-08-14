package com.bizkredit.sme.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * sme-loan-service's first outbound Feign client (to monitoring-service for
 * notifications) needed its own request-interceptor config - previously
 * this service only ever received calls, never made any, so no such class
 * existed yet.
 *
 * monitoring-service's POST /api/notifications is ADMIN-only, because
 * raising a notification is a system-level side effect of a business
 * action (e.g. an application status change), not something the
 * triggering role should need ADMIN rights for. So unlike a typical
 * forwarded-JWT Feign call, this one always uses a short-lived system
 * token instead of the original caller's token.
 */
@Configuration
public class FeignClientConfig {

    @Autowired
    private JwtUtil jwtUtil;

    @Bean
    public RequestInterceptor authForwardingInterceptor() {
        return template -> {
            String systemToken = jwtUtil.generateSystemToken("system-sme-loan-service", "ADMIN");
            template.header("Authorization", "Bearer " + systemToken);
        };
    }

}
