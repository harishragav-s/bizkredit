package com.bizkredit.credit.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Authenticates outbound Feign calls to sme-loan-service (and auth-service's
 * role lookup / monitoring-service's notification endpoint).
 * **/
@Configuration
public class FeignClientConfig {

    @Autowired
    private JwtUtil jwtUtil;

    @Bean
    public RequestInterceptor authForwardingInterceptor() {
        return template -> {
            String systemToken = jwtUtil.generateSystemToken("system-credit-service", "ADMIN");
            template.header("Authorization", "Bearer " + systemToken);
        };
    }

}
