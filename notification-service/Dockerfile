# ── Stage 1: Build ──────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy parent POM and module POMs first for dependency caching
COPY pom.xml ./pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml
COPY job-service/pom.xml ./job-service/pom.xml
COPY worker-service/pom.xml ./worker-service/pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml

# Install Maven
RUN apk add --no-cache maven

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl notification-service -am -B -q 2>/dev/null || true

# Copy source code
COPY notification-service/src ./notification-service/src

# Build the service JAR
RUN mvn clean package -pl notification-service -am -DskipTests -B -q

# ── Stage 2: Runtime ───────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache wget

COPY --from=build /workspace/notification-service/target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
