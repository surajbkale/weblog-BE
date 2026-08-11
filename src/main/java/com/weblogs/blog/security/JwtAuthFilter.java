package com.weblogs.blog.security;

import com.weblogs.blog.user.Role;
import com.weblogs.blog.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.validateAndExtractClaims(token);

            // Reject suspended accounts immediately — no DB hit needed (claim is in JWT)
            if (!jwtService.extractActive(claims)) {
                writeUnauthorized(response, "Your account has been suspended. Please contact support.");
                return;
            }

            UUID userId  = jwtService.extractUserId(claims);
            String email = jwtService.extractEmail(claims);
            String role  = jwtService.extractRole(claims);

            // Build a lightweight principal from claims — no DB hit needed (truly stateless)
            User principal = User.builder()
                    .id(userId)
                    .email(email)
                    .role(Role.valueOf(role))
                    .emailVerified(true)
                    .active(true)
                    .build();

            var auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (JwtException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            writeUnauthorized(response, "Invalid or expired access token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Plain string — no Jackson dependency needed in the security filter layer
        response.getWriter().write(
            "{\"success\":false,\"data\":null,\"message\":\"" +
            message.replace("\"", "\\\"") + "\",\"errors\":null}"
        );
    }
}
