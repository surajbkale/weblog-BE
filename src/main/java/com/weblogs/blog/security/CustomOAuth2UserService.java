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

        String email      = extractEmail(provider, attributes);
        String providerId = extractProviderId(provider, attributes);
        String name       = extractDisplayName(attributes);
        String avatarUrl  = extractAvatarUrl(provider, attributes);

        User user = resolveUser(email, provider, providerId, name, avatarUrl);
        return new CustomOAuth2User(user, attributes);
    }

    private User resolveUser(String email, AuthProvider provider,
                             String providerId, String name, String avatarUrl) {
        // 1) Existing OAuth identity match
        Optional<User> byProvider = userRepository.findByProviderIdAndAuthProvider(providerId, provider);
        if (byProvider.isPresent()) {
            return byProvider.get();
        }

        // 2) Email match on a LOCAL account → link OAuth identity
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            log.info("Linking {} identity to existing LOCAL account: {}", provider, email);
            existing.setProviderId(providerId);
            existing.setEmailVerified(true);
            if (existing.getAvatarUrl() == null) existing.setAvatarUrl(avatarUrl);
            return userRepository.save(existing);
        }

        // 3) Brand-new user
        User newUser = User.builder()
                .email(email)
                .displayName(name)
                .avatarUrl(avatarUrl)
                .authProvider(provider)
                .providerId(providerId)
                .emailVerified(true)   // provider already verified the email
                .role(Role.USER)
                .build();
        return userRepository.save(newUser);
    }

    // ── Attribute extraction ──────────────────────────────────────────────────

    private String extractEmail(AuthProvider provider, Map<String, Object> attrs) {
        return switch (provider) {
            case GOOGLE -> (String) attrs.get("email");
            case GITHUB -> (String) attrs.get("email");
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        };
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
