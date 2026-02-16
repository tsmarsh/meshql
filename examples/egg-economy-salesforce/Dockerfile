FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

RUN apk add --no-cache maven

COPY . .

RUN mvn clean package -pl examples/egg-economy-salesforce -am -Dmaven.test.skip=true

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /app/examples/egg-economy-salesforce/target/egg-economy-salesforce-example-*.jar /app/egg-economy-salesforce-example.jar
COPY examples/egg-economy-salesforce/config /app/config

EXPOSE 5090

CMD ["java", "-jar", "/app/egg-economy-salesforce-example.jar"]
