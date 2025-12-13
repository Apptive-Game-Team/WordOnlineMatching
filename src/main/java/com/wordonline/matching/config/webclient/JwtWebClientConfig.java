package com.wordonline.matching.config.webclient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class JwtWebClientConfig {

    @Value("${team6515.jwt.path:}")
    private String jwtPath;

    @Bean
    public WebClient.Builder webClientBuilder() {
        String jwtToken = loadJwtToken();
        
        WebClient.Builder builder = WebClient.builder();
        
        if (jwtToken != null && !jwtToken.isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
        }
        
        return builder;
    }

    private String loadJwtToken() {
        if (jwtPath == null || jwtPath.isEmpty()) {
            log.warn("JWT path not configured, WebClient will not have authentication token");
            return "";
        }

        try {
            String token = Files.readString(Paths.get(jwtPath)).trim();
            log.info("JWT token loaded from: {}", jwtPath);
            return token;
        } catch (IOException e) {
            log.error("Failed to load JWT token from path: {}", jwtPath, e);
            return "";
        }
    }
}
