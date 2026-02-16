package com.meshql.repositories.ksql;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Properties;

/**
 * Shared test environment for Kafka + ksqlDB containers.
 * Uses testcontainers' built-in ConfluentKafkaContainer with an additional
 * internal listener for ksqlDB to connect via Docker network.
 */
public class KsqlTestEnvironment {
    private static final Logger logger = LoggerFactory.getLogger(KsqlTestEnvironment.class);

    private static Network network;
    private static ConfluentKafkaContainer kafka;
    private static GenericContainer<?> ksqlDb;

    private static String bootstrapServers;
    private static String ksqlDbUrl;
    private static boolean started = false;

    public static synchronized void start() {
        if (started) return;

        logger.info("Starting Kafka + ksqlDB test containers...");

        network = Network.newNetwork();

        // Start Kafka using testcontainers' built-in ConfluentKafkaContainer
        // withListener adds an internal listener for ksqlDB to connect via Docker network
        kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.5.0")
                .withNetwork(network)
                .withNetworkAliases("kafka")
                .withListener("kafka:19092");
        kafka.start();

        bootstrapServers = kafka.getBootstrapServers();
        logger.info("Kafka started at: {}", bootstrapServers);

        // Start ksqlDB, connecting to Kafka via internal listener on Docker network
        ksqlDb = new GenericContainer<>(DockerImageName.parse("confluentinc/ksqldb-server:0.29.0"))
                .withNetwork(network)
                .withNetworkAliases("ksqldb")
                .withExposedPorts(8088)
                .withEnv("KSQL_BOOTSTRAP_SERVERS", "kafka:19092")
                .withEnv("KSQL_LISTENERS", "http://0.0.0.0:8088")
                .withEnv("KSQL_KSQL_STREAMS_COMMIT_INTERVAL_MS", "100")
                .withEnv("KSQL_KSQL_STREAMS_CACHE_MAX_BYTES_BUFFERING", "0")
                .withEnv("KSQL_KSQL_STREAMS_AUTO_OFFSET_RESET", "earliest")
                .withEnv("KSQL_KSQL_QUERY_PULL_TABLE_SCAN_ENABLED", "true")
                .waitingFor(Wait.forHttp("/info")
                        .forPort(8088)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(2)))
                .withStartupTimeout(Duration.ofMinutes(3));
        ksqlDb.start();

        ksqlDbUrl = "http://" + ksqlDb.getHost() + ":" + ksqlDb.getMappedPort(8088);
        logger.info("ksqlDB started at: {}", ksqlDbUrl);

        started = true;
    }

    public static synchronized void stop() {
        if (!started) return;

        if (ksqlDb != null) ksqlDb.stop();
        if (kafka != null) kafka.stop();
        if (network != null) network.close();

        started = false;
    }

    public static String getBootstrapServers() {
        return bootstrapServers;
    }

    public static String getKsqlDbUrl() {
        return ksqlDbUrl;
    }

    public static KafkaProducer<String, String> createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(props);
    }

    public static KsqlHttpClient createKsqlClient() {
        return new KsqlHttpClient(ksqlDbUrl);
    }

    public static KsqlConfig createConfig(String topic) {
        KsqlConfig config = new KsqlConfig();
        config.bootstrapServers = bootstrapServers;
        config.ksqlDbUrl = ksqlDbUrl;
        config.topic = topic;
        config.partitions = 1;
        config.replicationFactor = 1;
        config.autoCreate = true;
        config.acks = "all";
        return config;
    }
}
