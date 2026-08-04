package com.weblogs.blog.security;

import com.weblogs.blog.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

/**
 * Wrapper that carries the persisted {@link User} entity through the OAuth2 login
 * success flow, implementing both {@link OAuth2User} and {@link UserDetails}.
 */
public class CustomOAuth2User implements OAuth2User, UserDetails {

    private final User user;
    private final Map<String, Object> attributes;

    public CustomOAuth2User(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    public User getUser() {
        return user;
    }

    // ── OAuth2User ────────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return user.getId().toString();
    }

    // ── UserDetails ───────────────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getAuthorities();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }
}
