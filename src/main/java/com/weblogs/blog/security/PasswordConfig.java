package com.weblogs.blog.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Isolated configuration for password encoding.
 *
 * <p>This MUST be a separate class from {@link SecurityConfig} to break the circular
 * dependency chain:
 *
 * <pre>
 *   AuthService
 *     → PasswordEncoder            ← was in SecurityConfig
 *     → SecurityConfig
 *     → OAuth2LoginSuccessHandler
 *     → AuthService               ← cycle!
 * </pre>
 *
 * <p>By moving {@code PasswordEncoder} here, {@code AuthService} no longer depends on
 * {@code SecurityConfig} and the cycle is eliminated.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // strength 12 — industry standard
    }
}
