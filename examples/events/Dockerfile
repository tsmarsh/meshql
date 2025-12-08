FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy the entire project for building
COPY . .

# Build the events example (will build dependencies first)
RUN mvn clean package -pl examples/events -am -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install curl for healthcheck
RUN apk add --no-cache curl

# Copy the built JAR
COPY --from=builder /app/examples/events/target/events-example-*.jar /app/events-example.jar

# Copy configuration files
COPY examples/events/config /app/config

# Create symlink for Docker path expectations
RUN mkdir -p /app/config && ln -sf /app/config /app/config

# Expose the application port
EXPOSE 4055

# Run the application
CMD ["java", "-jar", "/app/events-example.jar"]
