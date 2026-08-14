package com.bizkredit.credit.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Configuration class for Swagger OpenAPI documentation - Credit Service
@Configuration
public class OpenApiConfig {

    // Name used to define JWT Bearer authentication scheme in Swagger
    private static final String SCHEME_NAME = "bearerAuth";

    // OpenAPI configuration for this microservice
    @Bean
    public OpenAPI creditServiceOpenAPI() {
        return new OpenAPI()

                // Basic API metadata shown in Swagger UI
                .info(new Info()
                        .title("BizKredit - Credit Service")
                        .description("Credit Analysis & Scorecard microservice")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("BizKredit Team")
                                .email("support@bizkredit.com"))
                        .license(new License()
                                .name("Proprietary")))

                // Apply security (JWT Bearer) globally for all APIs
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))

                // Define security scheme (Authorization header with Bearer JWT)
                .components(new Components()
                        .addSecuritySchemes(SCHEME_NAME, new SecurityScheme()
                                .name(SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the JWT returned by POST /api/auth/login on auth-service")));
    }
}
