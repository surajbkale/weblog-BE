package com.weblogs.blog.security;

import com.weblogs.blog.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

        String providerId      = extractProviderId(provider, attributes);
        String email           = extractEmail(provider, attributes, providerId);
        String name            = extractDisplayName(attributes);
        String avatarUrl       = extractAvatarUrl(provider, attributes);
        boolean syntheticEmail = isSyntheticEmail(email);

        User user = resolveUser(email, syntheticEmail, provider, providerId, name, avatarUrl);
        return new CustomOAuth2User(user, attributes);
    }

    private User resolveUser(String email, boolean syntheticEmail, AuthProvider provider,
                             String providerId, String name, String avatarUrl) {
        // 1) Existing OAuth identity match (always try first — no email needed)
        Optional<User> byProvider = userRepository.findByProviderIdAndAuthProvider(providerId, provider);
        if (byProvider.isPresent()) {
            return byProvider.get();
        }

        // 2) Email match on a LOCAL account → link OAuth identity.
        //    Only attempt when we have a real email (not a synthetic noreply address).
        if (!syntheticEmail) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                User existing = byEmail.get();
                log.info("Linking {} identity to existing LOCAL account: {}", provider, email);
                existing.setProviderId(providerId);
                existing.setEmailVerified(true);
                if (existing.getAvatarUrl() == null) existing.setAvatarUrl(avatarUrl);
                return userRepository.save(existing);
            }
        }

        // 3) Brand-new user
        log.debug("Creating new user via {} OAuth. email={} synthetic={}", provider, email, syntheticEmail);
        User newUser = User.builder()
                .email(email)
                .displayName(name)
                .avatarUrl(avatarUrl)
                .authProvider(provider)
                .providerId(providerId)
                .emailVerified(!syntheticEmail)   // real email = already verified by provider
                .role(Role.USER)
                .build();
        return userRepository.save(newUser);
    }

    // ── Attribute extraction ──────────────────────────────────────────────────

    /**
     * Extract the email from OAuth attributes.
     * <p>
     * GitHub users may have their email set to private, in which case the API returns
     * {@code null}. We synthesise a stable noreply address in that case:
     * {@code {providerId}+{login}@users.noreply.github.com}
     * (the same pattern GitHub uses for git commit noreply addresses).
     * </p>
     */
    private String extractEmail(AuthProvider provider, Map<String, Object> attrs, String providerId) {
        return switch (provider) {
            case GOOGLE -> {
                String email = (String) attrs.get("email");
                if (email == null || email.isBlank()) {
                    throw new OAuth2AuthenticationException("Google OAuth did not return an email address");
                }
                yield email.trim().toLowerCase();
            }
            case GITHUB -> {
                String email = (String) attrs.get("email");
                if (email != null && !email.isBlank()) {
                    yield email.trim().toLowerCase();
                }
                // GitHub private-email fallback — stable and unique per GitHub user ID
                String login    = String.valueOf(attrs.getOrDefault("login", providerId));
                String synthetic = providerId + "+" + login + "@users.noreply.github.com";
                log.debug("GitHub user has private email; using synthetic address: {}", synthetic);
                yield synthetic;
            }
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        };
    }

    /** Returns {@code true} when the email was synthesised (not a real user-provided address). */
    private boolean isSyntheticEmail(String email) {
        return email != null && email.endsWith("@users.noreply.github.com");
    }

    private String extractProviderId(AuthProvider provider, Map<String, Object> attrs) {
        return switch (provider) {
            case GOOGLE -> (String) attrs.get("sub");
            case GITHUB -> String.valueOf(attrs.get("id"));
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        };
    }

    private String extractDisplayName(Map<String, Object> attrs) {
        String name = (String) attrs.getOrDefault("name", attrs.getOrDefault("login", "User"));
        return name != null ? name : "User";
    }

    private String extractAvatarUrl(AuthProvider provider, Map<String, Object> attrs) {
        return switch (provider) {
            case GOOGLE -> (String) attrs.get("picture");
            case GITHUB -> (String) attrs.get("avatar_url");
            default -> null;
        };
    }
}
