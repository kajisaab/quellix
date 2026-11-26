# syntax=docker/dockerfile:1

# ================================================
# Stage 1: Build
# ================================================
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

# COPY Gradle wrapper and configuration first, so the dependency step below
# is rebuilt only when these files change, not on every source change.
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

# Make Gradle wrapper executable.
RUN chmod +x gradlew

# Download dependencies. The cache mount keeps Gradle's download cache between builds.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

# COPY application source.
COPY src ./src

# Build executable Spring Boot JAR (tests run in CI, not in the image build).
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon

# Split the jar into layers, ordered from least to most frequently changing.
RUN cp build/libs/*.jar application.jar && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ================================================
# Stage 2: Runtime
# ================================================
FROM eclipse-temurin:25-jre

# Build-time default port; override with --build-arg SERVER_PORT=9090
ARG SERVER_PORT=8080

WORKDIR /app

# curl for the HEALTHCHECK, and a non-root user that owns /app (so it can write logs/quellix.log).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --no-create-home spring \
    && chown spring:spring /app


COPY --from=builder --chown=spring:spring /app/extracted/dependencies/ ./
COPY --from=builder --chown=spring:spring /app/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /app/extracted/application/ ./

USER spring

# Run-time defaults; override with -e or --env-file.
ENV SERVER_PORT=${SERVER_PORT} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

EXPOSE ${SERVER_PORT}

# Marks the container unhealthy if /actuator/health stops answering 2xx.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS "http://localhost:${SERVER_PORT}/system/actuator/health" > /dev/null || exit 1

# Start application.
ENTRYPOINT ["java", "-jar", "application.jar"]
