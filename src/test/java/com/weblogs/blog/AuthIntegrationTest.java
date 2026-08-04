package com.weblogs.blog;

import com.weblogs.blog.user.AuthTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration test using real Postgres + Redis via Testcontainers.
 * Uses WebTestClient — the Spring Boot 4.x replacement for the removed
 * TestRestTemplate.
 * Requires spring-boot-starter-webflux on the test classpath (test scope only).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class AuthIntegrationTest {

        @Container
        @ServiceConnection
        static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

        @Container
        @SuppressWarnings("resource")
        static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

        /**
         * Wire Redis host/port into Spring context — GenericContainer has
         * no @ServiceConnection support.
         */
        @DynamicPropertySource
        static void redisProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.data.redis.host", redis::getHost);
                registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        }

        @Autowired
        private WebTestClient webTestClient;

        @Autowired
        private AuthTokenRepository authTokenRepository;

        // ── Infrastructure ────────────────────────────────────────────────────────

        @Test
        void containers_areRunningAndHealthy() {
                assertThat(postgres.isRunning()).isTrue();
                assertThat(redis.isRunning()).isTrue();
        }

        // ── No-enumeration: register always returns 200 ───────────────────────────

        @Test
        void register_newUser_returns200WithGenericMessage() {
                webTestClient.post().uri("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue("""
                                                {"email":"newuser@example.com","password":"Test@1234","displayName":"New User"}
                                                """)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(true);
        }

        @Test
        void register_duplicateEmail_stillReturns200_noEnumeration() {
                String body = """
                                {"email":"duplicate@example.com","password":"Test@1234","displayName":"User"}
                                """;

                // First registration
                webTestClient.post().uri("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(body)
                                .exchange()
                                .expectStatus().isOk();

                // Second with same email — must ALSO return 200 (no user enumeration leak)
                webTestClient.post().uri("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(body)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(true);
        }

        // ── Login failure ─────────────────────────────────────────────────────────

        @Test
        void login_nonExistentUser_returns401() {
                webTestClient.post().uri("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue("""
                                                {"email":"nobody@example.com","password":"Wrong@1234"}
                                                """)
                                .exchange()
                                .expectStatus().isUnauthorized()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(false);
        }

        // ── Protected endpoint without token ──────────────────────────────────────

        @Test
        void getMe_withoutToken_returns401() {
                webTestClient.get().uri("/api/v1/users/me")
                                .exchange()
                                .expectStatus().isUnauthorized()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(false);
        }

        // ── Bean Validation ───────────────────────────────────────────────────────

        @Test
        void register_missingFields_returns400WithErrors() {
                webTestClient.post().uri("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue("{}")
                                .exchange()
                                .expectStatus().isBadRequest()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(false)
                                .jsonPath("$.errors").isArray();
        }

        @Test
        void register_weakPassword_returns400() {
                webTestClient.post().uri("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue("""
                                                {"email":"test@example.com","password":"weak","displayName":"User"}
                                                """)
                                .exchange()
                                .expectStatus().isBadRequest();
        }

        @Test
        void forgotPassword_anyEmail_alwaysReturns200_noEnumeration() {
                webTestClient.post().uri("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue("""
                                                {"email":"doesnotexist@example.com"}
                                                """)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(true);
        }
}
