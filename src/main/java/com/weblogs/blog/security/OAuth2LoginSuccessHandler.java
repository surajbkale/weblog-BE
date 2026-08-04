package com.weblogs.blog.security;

import com.weblogs.blog.auth.AuthService;
import com.weblogs.blog.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 success handler — issues a refresh token cookie and redirects the browser
 * to the frontend callback page. The frontend then calls /api/v1/auth/refresh to
 * exchange the cookie for an access token.
 *
 * <p>The access token is intentionally NOT placed in the redirect URL to prevent
 * leakage via browser history, Referer headers, or server access logs.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        // Issue refresh token cookie — the frontend will immediately call /refresh
        authService.issueAndSetRefreshCookie(user, response);

        log.debug("OAuth2 login success for user: {}", user.getEmail());
        response.sendRedirect(frontendUrl + "/oauth/callback");
    }
}
