package com.bizkredit.collateral.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfig {

    @Autowired
    private JwtUtil jwtUtil;

    @Bean
    public RequestInterceptor authForwardingInterceptor() {
        return template -> {
            String systemToken = jwtUtil.generateSystemToken("system-collateral-service", "ADMIN");
            template.header("Authorization", "Bearer " + systemToken);
        };
    }

}
