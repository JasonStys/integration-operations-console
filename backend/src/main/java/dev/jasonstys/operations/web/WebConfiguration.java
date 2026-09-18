/*
 * File: WebConfiguration.java
 * Purpose: Restricts browser cross-origin access to the configured UI origin.
 * Symbols: allowedOrigin field and addCorsMappings(); exact lines are in docs/code-index.md.
 */
package dev.jasonstys.operations.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Minimal CORS policy; command-line clients are unaffected. */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final String allowedOrigin;

    /** @param allowedOrigin exact browser origin from application configuration */
    public WebConfiguration(@Value("${operations.allowed-origin}") String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "Idempotency-Key", "X-Correlation-Id", "X-Demo-Role", "X-Demo-Actor")
                .exposedHeaders("Location", "X-Correlation-Id")
                .maxAge(3600);
    }
}
