FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

RUN apk add --no-cache maven

COPY . .

RUN mvn clean package -pl examples/egg-economy-sap -am -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /app/examples/egg-economy-sap/target/egg-economy-sap-example-*.jar /app/egg-economy-sap-example.jar
COPY examples/egg-economy-sap/config /app/config

EXPOSE 5089

CMD ["java", "-jar", "/app/egg-economy-sap-example.jar"]
