# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy Maven wrapper and pom files
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY common/pom.xml common/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml

# Download dependencies
RUN ./mvnw dependency:go-offline -pl api-gateway -am

# Copy source code
COPY common/src common/src
COPY api-gateway/src api-gateway/src

# Build application
RUN ./mvnw clean package -pl api-gateway -am -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built artifact
COPY --from=build /workspace/api-gateway/target/*.jar app.jar

# JVM configuration
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 \
               -XX:+UseZGC \
               -XX:+ZGenerational \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
