package com.bizkredit.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI authServiceOpenAPI() {
        return new OpenAPI()

                .info(new Info()
                        .title("BizKredit - Auth Service")
                        .description("Authentication, Users, RBAC Scope & Audit Trail microservice")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("BizKredit Team")
                                .email("support@bizkredit.com"))
                        .license(new License()
                                .name("Proprietary")))

                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))

                .components(new Components()
                        .addSecuritySchemes(SCHEME_NAME, new SecurityScheme()
                                .name(SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the JWT returned by POST /api/auth/login")));
    }
}

