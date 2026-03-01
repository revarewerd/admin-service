# ============================================================
# Admin Service — Multi-stage Docker build
# ============================================================

FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY project/build.properties project/
COPY project/plugins.sbt project/
COPY build.sbt .

RUN sbt update

COPY src/ src/

RUN sbt assembly

# ============================================================
# Runtime
# ============================================================

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S adminservice && \
    adduser -S adminservice -G adminservice

COPY --from=builder /app/target/scala-3.4.0/admin-service-assembly-*.jar app.jar

USER adminservice

EXPOSE 8097

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8097/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
