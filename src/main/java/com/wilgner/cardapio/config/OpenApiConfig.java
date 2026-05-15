package com.wilgner.cardapio.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String bearerSecuritySchemeName = "bearerAuth";
        final String cookieSecuritySchemeName = "cookieAuth";

        return new OpenAPI()
                .info(new Info().title("cardapio-online").version("1.0"))
                .addSecurityItem(new SecurityRequirement().addList(bearerSecuritySchemeName))
                .addSecurityItem(new SecurityRequirement().addList(cookieSecuritySchemeName))
                .components(new Components()
                        .addSecuritySchemes(bearerSecuritySchemeName,
                                new SecurityScheme()
                                        .name(bearerSecuritySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addSecuritySchemes(cookieSecuritySchemeName,
                                new SecurityScheme()
                                        .name("access_token")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)));
    }
}
