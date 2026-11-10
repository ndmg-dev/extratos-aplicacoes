package com.mendoncagalvao.extratos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Libera a origem do frontend em dev (§7). */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] origens;

    public CorsConfig(@Value("${cors.allowed-origins:http://localhost:5173}") String origens) {
        this.origens = origens.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origens)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(false);
    }
}
