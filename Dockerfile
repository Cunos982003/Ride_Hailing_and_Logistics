FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# Copy Maven wrapper and pom files
COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY location-service/pom.xml location-service/pom.xml
COPY dispatch-service/pom.xml dispatch-service/pom.xml
COPY pricing-service/pom.xml pricing-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY ws-gateway/pom.xml ws-gateway/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY common/src common/src
COPY user-service/src user-service/src
COPY location-service/src location-service/src
COPY dispatch-service/src dispatch-service/src
COPY pricing-service/src pricing-service/src
COPY payment-service/src payment-service/src
COPY ws-gateway/src ws-gateway/src
COPY api-gateway/src api-gateway/src

# Build the application
ARG MODULE
RUN ./mvnw clean package -pl ${MODULE} -am -DskipTests -Dspotless.check.skip -Dcheckstyle.skip

# Extract layers
WORKDIR /workspace/${MODULE}/target
RUN java -Djarmode=layertools -jar *.jar extract

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Add non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy layers from builder
ARG MODULE
COPY --from=builder /workspace/${MODULE}/target/dependencies/ ./
COPY --from=builder /workspace/${MODULE}/target/spring-boot-loader/ ./
COPY --from=builder /workspace/${MODULE}/target/snapshot-dependencies/ ./
COPY --from=builder /workspace/${MODULE}/target/application/ ./

# Healthcheck script used by compose.yaml
RUN printf '#!/bin/sh\ncurl -sf http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1\n' > /app/healthcheck.sh \
    && chmod +x /app/healthcheck.sh

# Set ownership
RUN chown -R appuser:appuser /app
USER appuser

# JVM settings
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
