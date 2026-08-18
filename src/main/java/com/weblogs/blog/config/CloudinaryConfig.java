package com.weblogs.blog.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a Cloudinary SDK bean configured from {@code app.cloudinary.*} properties.
 * L-2: Logs a startup warning when credentials are missing so failures surface early
 * rather than as an opaque SDK error on the first upload request.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class CloudinaryConfig {

    private final AppProperties appProperties;

    @PostConstruct
    void validateCloudinaryConfig() {
        AppProperties.Cloudinary cfg = appProperties.getCloudinary();
        if (cfg.getCloudName() == null || cfg.getCloudName().isBlank()
                || cfg.getApiKey() == null || cfg.getApiKey().isBlank()
                || cfg.getApiSecret() == null || cfg.getApiSecret().isBlank()) {
            log.warn("Cloudinary credentials are not configured (app.cloudinary.*). " +
                     "Media uploads will fail at runtime. Set CLOUDINARY_CLOUD_NAME, " +
                     "CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET in your environment.");
        }
    }

    @Bean
    public Cloudinary cloudinary() {
        AppProperties.Cloudinary cfg = appProperties.getCloudinary();
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cfg.getCloudName(),
                "api_key",    cfg.getApiKey(),
                "api_secret", cfg.getApiSecret(),
                "secure",     true
        ));
    }
}
