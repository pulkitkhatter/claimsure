package com.claimsure.identity;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("ClaimSure Identity API").version("v1")
                        .description("Registration, login and JWT issuing"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
