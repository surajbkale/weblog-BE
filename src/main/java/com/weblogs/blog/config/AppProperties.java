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
    private String siteUrl     = "http://localhost:8080";

    private Mail mail = new Mail();
    private RateLimit rateLimit = new RateLimit();
    private Cloudinary cloudinary = new Cloudinary();
    private Cache cache = new Cache();
    private Cookies cookies = new Cookies();
    private Media media = new Media();

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
        private long trendingTtlSeconds     = 300;    // 5 min
        private long featuredTtlSeconds     = 600;    // 10 min
        private int  trendingWindowDays     = 7;      // lookback window for trending
        private int  trendingLimit          = 10;     // max posts in trending list
        private int  featuredLimit          = 10;     // max posts in featured list
        private int  feedLimit              = 20;     // posts included in RSS feed
    }

    /**
     * Cookie security settings.
     * Set {@code app.cookies.secure=false} in local dev (or via COOKIES_SECURE env var)
     * to allow cookies over plain HTTP. Production should always leave this {@code true}.
     */
    @Getter
    @Setter
    public static class Cookies {
        /** When true, all auth cookies are flagged Secure (HTTPS-only). Default: true. */
        private boolean secure = true;
    }

    /**
     * Media upload size limits.
     * These values drive the application-level validation in {@code MediaService}.
     * The Spring multipart limits in {@code spring.servlet.multipart} should be set
     * to the same or higher values so the HTTP layer never rejects before we do.
     */
    @Getter
    @Setter
    public static class Media {
        /** Maximum image upload size in bytes. Default: 5 MB. */
        private long imageMaxSizeBytes = 5L * 1024 * 1024;
        /** Maximum video upload size in bytes. Default: 50 MB. */
        private long videoMaxSizeBytes = 50L * 1024 * 1024;
    }
}

