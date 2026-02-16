FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

RUN apk add --no-cache maven

COPY . .

RUN mvn clean package -pl examples/enterprise -am -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /app/examples/enterprise/target/enterprise-example-*.jar /app/enterprise-example.jar
COPY examples/enterprise/config /app/config

EXPOSE 5091

CMD ["java", "-jar", "/app/enterprise-example.jar"]
