# syntax=docker/dockerfile:1
FROM eclipse-temurin:24-jdk-alpine AS builder

WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .

# Cache Gradle dependencies as a separate layer — re-runs only when build.gradle changes
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon -q

COPY src src

# Build uses the cached ~/.gradle — no re-download on source-only changes
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon

FROM eclipse-temurin:24-jre-alpine

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
