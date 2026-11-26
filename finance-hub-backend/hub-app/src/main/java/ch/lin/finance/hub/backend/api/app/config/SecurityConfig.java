/*
 * Copyright (c) 2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin. Unauthorized copying,
 * modification, distribution, reverse engineering, or use of the Software,
 * via any medium, is strictly prohibited without prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ch.lin.finance.hub.backend.api.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configures the application's security settings using Spring Security.
 * <p>
 * This class provides two distinct {@link SecurityFilterChain} beans that are
 * conditionally enabled based on the {@code app.security.enabled} property.
 * This allows for easily switching between a secure (production) and an
 * insecure (development/testing) mode.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Determines whether Swagger UI and OpenAPI documentation endpoints are
     * publicly accessible.
     * If true, no authentication is required to view the API documentation.
     * Defaults to false.
     */
    @Value("${app.security.swagger-public:false}")
    private boolean isSwaggerPublic;

    /**
     * Configures the main security filter chain, which is active by default or
     * when {@code app.security.enabled} is {@code true}. This chain secures all
     * endpoints except for {@code /health}, requiring a valid JWT for access.
     * It operates in a stateless manner, suitable for REST APIs.
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain enabledSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/health").permitAll();
                    if (isSwaggerPublic) {
                        authorize.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    } else {
                        authorize.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                .authenticated();
                    }
                    authorize.anyRequest().authenticated();
                })
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Configures a "no-security" filter chain, active only when
     * {@code app.security.enabled} is explicitly set to {@code false}. This
     * chain permits all requests and disables CSRF protection, making it
     * suitable for local development, testing, or environments where security
     * is handled externally.
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")
    public SecurityFilterChain disabledSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
