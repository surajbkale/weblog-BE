# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copy Maven wrapper and pom first (layer-caches dependencies)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build the fat JAR (skip tests — run them in CI separately)
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Using the Debian-based JRE (not Alpine/musl) to avoid a known musl-libc DNS
# resolution bug that causes UnknownHostException for Docker service names.
FROM eclipse-temurin:17-jre AS runtime

# Non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring
USER spring

WORKDIR /app

# Copy the fat JAR from the build stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Pass profile via SPRING_PROFILES_ACTIVE env var (override in docker-compose)
ENTRYPOINT ["java", "-jar", "app.jar"]
