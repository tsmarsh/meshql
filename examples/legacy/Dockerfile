FROM maven:3.9-eclipse-temurin-25-alpine AS builder

WORKDIR /build

# Copy the entire project
COPY . .

# Build the legacy example (this will build all dependencies too)
RUN mvn clean package -pl examples/legacy -am -DskipTests

# Runtime stage
FROM eclipse-temurin:25-jre-alpine

# Install curl for healthchecks
RUN apk add --no-cache curl

WORKDIR /app

# Copy the uber JAR from builder stage
COPY --from=builder /build/examples/legacy/target/legacy-example-0.2.0.jar /app/legacy-example.jar

# Copy config files
COPY --from=builder /build/examples/legacy/config /app/config

# Expose the application port
EXPOSE 4066

# Run the uber JAR
CMD ["java", "-jar", "/app/legacy-example.jar"]
