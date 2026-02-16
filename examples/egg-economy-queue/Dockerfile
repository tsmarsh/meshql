FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

RUN apk add --no-cache maven

COPY . .

RUN mvn clean package -pl examples/egg-economy-queue -am -Dmaven.test.skip=true

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /app/examples/egg-economy-queue/target/egg-economy-queue-example-*.jar /app/app.jar
COPY examples/egg-economy-queue/config /app/config
COPY examples/egg-economy-queue/lib/libmerksql_jni.so /usr/lib/

RUN mkdir -p /app/data

EXPOSE 5088

CMD ["java", "--enable-native-access=ALL-UNNAMED", "-Djava.library.path=/usr/lib", "-jar", "/app/app.jar"]
