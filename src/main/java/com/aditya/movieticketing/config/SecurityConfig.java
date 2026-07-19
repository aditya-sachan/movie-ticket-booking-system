package com.aditya.movieticketing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TEMPORARY security configuration for slices 2-4.
 *
 * <p>Permits every request so the hold/confirm/cancel flow can be exercised without
 * authentication. Slice 5 replaces this entirely with HTTP Basic auth backed by
 * {@code app_user} rows and method-level {@code @PreAuthorize} RBAC, at which point the
 * {@code userId} carried in booking requests is replaced by the authenticated principal.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
