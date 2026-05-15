package com.wilgner.cardapio.config;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.MultipartConfigElement;

@Configuration
public class MultipartConfig {
    private final DataSize maxFileSize;
    private final DataSize maxRequestSize;

    public MultipartConfig(
            @Value("${spring.servlet.multipart.max-file-size:5MB}") DataSize maxFileSize,
            @Value("${spring.servlet.multipart.max-request-size:5MB}") DataSize maxRequestSize) {
        this.maxFileSize = maxFileSize;
        this.maxRequestSize = maxRequestSize;
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();

        factory.setMaxFileSize(maxFileSize);
        factory.setMaxRequestSize(maxRequestSize);

        return factory.createMultipartConfig();
    }
}
