FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

RUN apk add --no-cache maven

COPY . .

RUN mvn clean package -pl examples/egg-economy-ksqldb -am -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /app/examples/egg-economy-ksqldb/target/egg-economy-ksqldb-example-*.jar /app/egg-economy-ksqldb-example.jar
COPY examples/egg-economy-ksqldb/config /app/config

EXPOSE 5090

CMD ["java", "-jar", "/app/egg-economy-ksqldb-example.jar"]
