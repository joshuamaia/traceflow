# syntax=docker/dockerfile:1
# Build context: repository root (see docker-compose.yml)

# ---------- build stage ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Optional extra CA certificates (corporate proxies / TLS inspection). docker/certs is empty by default.
COPY docker/certs/ /tmp/certs/
RUN set -e; for f in /tmp/certs/*.crt; do \
      [ -f "$f" ] || continue; \
      keytool -importcert -noprompt -cacerts -storepass changeit -alias "extra-$(basename "$f")" -file "$f"; \
    done

COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src/ src/
RUN ./mvnw -B -q package -DskipTests && cp target/traceflow-backend.jar /workspace/app.jar

# ---------- runtime stage ----------
FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system traceflow && useradd --system --gid traceflow --home /app traceflow \
    && mkdir -p /data/uploads /app \
    && chown -R traceflow:traceflow /data /app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER traceflow

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    TRACEFLOW_STORAGE_DIR=/data/uploads
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=10 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
