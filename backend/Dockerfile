# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolve in their own layer, so a code change does not re-download
# the world on every build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ------------------------------------------------------------------ run stage
FROM eclipse-temurin:21-jre-alpine AS runtime

# Runs as a non-root user: a container that is compromised should not also be
# root inside its own filesystem.
RUN addgroup -S kra && adduser -S kra -G kra
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

# Exports are written to disk before they are served, so the directory has to
# exist and be writable by the same user that runs the process.
RUN mkdir -p /app/exports && chown -R kra:kra /app
USER kra

EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx: the JVM then sizes itself from the
# container's limit instead of the host's memory, which is what makes a
# memory-limited container behave.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=prod
ENV REPORT_EXPORT_DIR=/app/exports

# The orchestrator reads readiness from Actuator rather than guessing from the
# port being open: the port answers long before Flyway has finished.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q UP || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
