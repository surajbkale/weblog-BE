package com.weblogs.blog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String frontendUrl = "http://localhost:3000";

    private Mail mail = new Mail();
    private RateLimit rateLimit = new RateLimit();
    private Cloudinary cloudinary = new Cloudinary();
    private Cache cache = new Cache();

    @Getter
    @Setter
    public static class Mail {
        private String from = "noreply@weblogs.com";
    }

    @Getter
    @Setter
    public static class RateLimit {
        private Login login = new Login();

        @Getter
        @Setter
        public static class Login {
            private int maxAttempts = 5;
            private int windowSeconds = 60;
        }
    }

    @Getter
    @Setter
    public static class Cloudinary {
        private String cloudName;
        private String apiKey;
        private String apiSecret;
    }

    @Getter
    @Setter
    public static class Cache {
        private long postListTtlSeconds     = 300;
        private long postBySlugTtlSeconds   = 600;
        private long tagsTtlSeconds         = 3600;
        private long categoriesTtlSeconds   = 3600;
        private long viewFlushIntervalMs    = 300_000;
    }
}

