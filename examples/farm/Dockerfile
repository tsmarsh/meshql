FROM maven:3.9-eclipse-temurin-25-alpine AS builder

WORKDIR /build

# Copy the entire project
COPY . .

# Build the farm example (this will build all dependencies too)
RUN mvn clean package -pl examples/farm -am -DskipTests

# Runtime stage
FROM eclipse-temurin:25-jre-alpine

# Install curl for healthchecks
RUN apk add --no-cache curl

WORKDIR /app

# Copy the uber JAR from builder stage
COPY --from=builder /build/examples/farm/target/farm-example-0.2.0.jar /app/farm-example.jar

# Copy config files
COPY --from=builder /build/examples/farm/config /app/config

# Expose the port
EXPOSE 3033

# Run the uber JAR
CMD ["java", "-jar", "/app/farm-example.jar"] 