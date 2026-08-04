package com.weblogs.blog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing so that {@code @CreatedDate} and {@code @LastModifiedDate}
 * on entities are populated automatically.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
public class JpaConfig {
}
