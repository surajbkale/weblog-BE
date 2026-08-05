package com.weblogs.blog.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a Cloudinary SDK bean configured from {@code app.cloudinary.*} properties.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class CloudinaryConfig {

    private final AppProperties appProperties;

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
