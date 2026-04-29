# ── Stage 1: Build ──────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy parent POM and module POMs first for dependency caching
COPY pom.xml ./pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml
COPY job-service/pom.xml ./job-service/pom.xml
COPY worker-service/pom.xml ./worker-service/pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml

# Install Maven wrapper
RUN apk add --no-cache maven

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl job-service -am -B -q 2>/dev/null || true

# Copy source code
COPY job-service/src ./job-service/src

# Build the service JAR
RUN mvn clean package -pl job-service -am -DskipTests -B -q

# ── Stage 2: Runtime ───────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache wget

COPY --from=build /workspace/job-service/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
