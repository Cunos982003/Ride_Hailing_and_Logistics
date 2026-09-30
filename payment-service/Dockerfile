# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy Maven wrapper and pom files
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY common/pom.xml common/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml

# Download dependencies
RUN ./mvnw dependency:go-offline -pl payment-service -am

# Copy source code
COPY common/src common/src
COPY payment-service/src payment-service/src

# Build application
RUN ./mvnw clean package -pl payment-service -am -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built artifact
COPY --from=build /workspace/payment-service/target/*.jar app.jar

# JVM configuration
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 \
               -XX:+UseZGC \
               -XX:+ZGenerational \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
