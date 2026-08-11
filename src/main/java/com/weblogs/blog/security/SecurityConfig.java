package com.weblogs.blog.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.weblogs.blog.user.UserRepository;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter             jwtAuthFilter;
    private final CustomOAuth2UserService   customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final UserRepository            userRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "Authentication required"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                        "Access denied"))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/**",
                    "/oauth2/**",
                    "/login/oauth2/**"
                ).permitAll()
                // Public read-only content endpoints (JWT optional — filter passes through)
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                    "/api/v1/posts",
                    "/api/v1/posts/{slug}",
                    "/api/v1/posts/*/comments",
                    "/api/v1/categories",
                    "/api/v1/tags"
                ).permitAll()
                // Admin-only endpoints — ROLE_ADMIN required at URL level
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(info -> info.userService(customOAuth2UserService))
                .successHandler(oAuth2LoginSuccessHandler)
                .failureHandler((request, response, exception) ->
                    writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "OAuth2 authentication failed: " + exception.getMessage()))
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return email -> userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    /**
     * Spring Security 7.x: DaoAuthenticationProvider now requires UserDetailsService
     * in the constructor — no-arg constructor and setUserDetailsService() were removed.
     *
     * With proxyBeanMethods=false, dependencies must come in as method parameters
     * so Spring injects the singleton beans (not new instances).
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Writes a minimal ApiResponse JSON without any Jackson dependency.
     * Security handlers must have zero heavy dependencies — if Jackson fails to
     * load, we still need to be able to write error responses.
     */
    private static void writeJson(HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
            "{\"success\":false,\"data\":null,\"message\":\"" +
            message.replace("\"", "\\\"") + "\",\"errors\":null}"
        );
    }
}
