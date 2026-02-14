FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

RUN apk add --no-cache maven

COPY . .

RUN mvn clean package -pl examples/egg-economy -am -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /app/examples/egg-economy/target/egg-economy-example-*.jar /app/egg-economy-example.jar
COPY examples/egg-economy/config /app/config

EXPOSE 5088

CMD ["java", "-jar", "/app/egg-economy-example.jar"]
