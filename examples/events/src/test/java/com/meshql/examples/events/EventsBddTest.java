package com.meshql.examples.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * BDD Tests for the Events CDC Pipeline
 *
 * These tests verify the end-to-end CDC pipeline:
 * HTTP POST → MongoDB → Debezium → Kafka → Processor → MongoDB
 *
 * NOTE: These tests require Docker and take ~60-90 seconds to run due to container startup.
 * They are skipped in CI environments (when CI=true).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EventsBddTest {

    private static final Logger logger = LoggerFactory.getLogger(EventsBddTest.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final long TEST_RUN_ID = System.currentTimeMillis();

    private Network network;
    private MongoDBContainer mongodb;
    private KafkaContainer kafka;
    private GenericContainer<?> debezium;
    private GenericContainer<?> eventsApp;

    private String eventsApiUrl;
    private String kafkaBootstrapServers;

    private static final String RAW_TOPIC = "events.events_development.event";
    private static final String PROCESSED_TOPIC = "events.events_development.processedevent";

    @BeforeAll
    void setUp() throws Exception {
        // Skip in CI environments
        if ("true".equals(System.getenv("CI"))) {
            logger.info("Skipping BDD tests in CI environment");
            return;
        }

        logger.info("Test run ID: {}", TEST_RUN_ID);
        logger.info("Starting test containers...");

        network = Network.newNetwork();

        // Start MongoDB with replica set
        mongodb = new MongoDBContainer(DockerImageName.parse("mongo:8"))
                .withNetwork(network)
                .withNetworkAliases("mongodb")
                .withCommand("--replSet", "rs0", "--bind_ip_all");
        mongodb.start();

        // Initialize replica set
        mongodb.execInContainer("mongosh", "--eval",
                "rs.initiate({_id:'rs0',members:[{_id:0,host:'mongodb:27017'}]})");
        Thread.sleep(5000); // Wait for replica set to initialize

        // Start Kafka
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                .withNetwork(network)
                .withNetworkAliases("kafka");
        kafka.start();

        kafkaBootstrapServers = kafka.getBootstrapServers();
        logger.info("Kafka started at: {}", kafkaBootstrapServers);

        // Start Debezium
        debezium = new GenericContainer<>(DockerImageName.parse("quay.io/debezium/server:2.6"))
                .withNetwork(network)
                .withNetworkAliases("debezium")
                .withExposedPorts(8080)
                .withEnv("DEBEZIUM_SOURCE_CONNECTOR_CLASS", "io.debezium.connector.mongodb.MongoDbConnector")
                .withEnv("DEBEZIUM_SOURCE_MONGODB_CONNECTION_STRING", "mongodb://mongodb:27017/?replicaSet=rs0")
                .withEnv("DEBEZIUM_SOURCE_TOPIC_PREFIX", "events")
                .withEnv("DEBEZIUM_SOURCE_DATABASE_INCLUDE_LIST", "events_development")
                .withEnv("DEBEZIUM_SOURCE_CAPTURE_MODE", "change_streams")
                .withEnv("DEBEZIUM_SOURCE_PUBLISH_FULL_DOCUMENT_ONLY", "true")
                .withEnv("DEBEZIUM_SOURCE_OFFSET_STORAGE", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
                .withEnv("DEBEZIUM_SOURCE_OFFSET_STORAGE_FILE_FILENAME", "/tmp/offsets.dat")
                .withEnv("DEBEZIUM_SOURCE_OFFSET_FLUSH_INTERVAL_MS", "0")
                .withEnv("DEBEZIUM_SINK_TYPE", "kafka")
                .withEnv("DEBEZIUM_SINK_KAFKA_PRODUCER_BOOTSTRAP_SERVERS", "kafka:9092")
                .withEnv("DEBEZIUM_SINK_KAFKA_PRODUCER_KEY_SERIALIZER", "org.apache.kafka.common.serialization.StringSerializer")
                .withEnv("DEBEZIUM_SINK_KAFKA_PRODUCER_VALUE_SERIALIZER", "org.apache.kafka.common.serialization.StringSerializer")
                .waitingFor(Wait.forHttp("/q/health").forPort(8080).forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(2));
        debezium.start();

        logger.info("Debezium started");

        // Build the events app JAR path
        String jarPath = System.getProperty("user.dir") + "/target/events-example-0.2.0.jar";

        // Start Events application
        eventsApp = new GenericContainer<>(DockerImageName.parse("eclipse-temurin:21-jre-alpine"))
                .withNetwork(network)
                .withNetworkAliases("events")
                .withExposedPorts(4055)
                .withFileSystemBind(jarPath, "/app/events-example.jar")
                .withFileSystemBind(System.getProperty("user.dir") + "/config", "/app/config")
                .withCommand("java", "-jar", "/app/events-example.jar")
                .withEnv("PORT", "4055")
                .withEnv("MONGO_URI", "mongodb://mongodb:27017/?replicaSet=rs0")
                .withEnv("PREFIX", "events")
                .withEnv("ENV", "development")
                .withEnv("PLATFORM_URL", "http://events:4055")
                .withEnv("KAFKA_BROKER", "kafka:9092")
                .withEnv("RAW_TOPIC", RAW_TOPIC)
                .withEnv("PROCESSED_API_BASE", "http://events:4055/processedevent/api")
                .waitingFor(Wait.forHttp("/health").forPort(4055).forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(2));
        eventsApp.start();

        eventsApiUrl = "http://" + eventsApp.getHost() + ":" + eventsApp.getMappedPort(4055);
        RestAssured.baseURI = eventsApiUrl;

        logger.info("Events app started at: {}", eventsApiUrl);
        logger.info("Setup complete, starting tests!");

        // Wait for all services to be ready
        Thread.sleep(5000);
    }

    @AfterAll
    void tearDown() {
        if (eventsApp != null) eventsApp.stop();
        if (debezium != null) debezium.stop();
        if (kafka != null) kafka.stop();
        if (mongodb != null) mongodb.stop();
        if (network != null) network.close();
    }

    @Test
    @Order(1)
    @DisplayName("Scenario 1: Event Service produces to Kafka")
    void scenario1_eventServiceProducesToKafka() throws Exception {
        assumeNotCI();

        String eventName = "bdd_test_1_" + TEST_RUN_ID;
        String correlationId = UUID.randomUUID().toString();

        logger.info("Scenario 1: Creating event with name={}, correlationId={}", eventName, correlationId);

        // Create Kafka consumer
        KafkaConsumer<String, String> consumer = createKafkaConsumer("bdd-test-1-" + System.currentTimeMillis());
        consumer.subscribe(List.of(RAW_TOPIC));

        // WHEN: I post an event to the event service
        String eventJson = mapper.writeValueAsString(new EventPayload(
                eventName,
                correlationId,
                "{\"test\": \"scenario_1\", \"timestamp\": " + System.currentTimeMillis() + "}",
                Instant.now().toString(),
                "bdd_test",
                "1.0"
        ));

        given()
                .contentType(ContentType.JSON)
                .body(eventJson)
                .when()
                .post("/event/api")
                .then()
                .statusCode(201);

        logger.info("Scenario 1: Event created, waiting for Kafka message...");

        // THEN: I should receive the event from Kafka
        String receivedEvent = waitForKafkaMessage(consumer, correlationId, Duration.ofSeconds(30));

        assertNotNull(receivedEvent, "Should receive event from Kafka");
        assertTrue(receivedEvent.contains(eventName), "Event should contain the correct name");
        assertTrue(receivedEvent.contains(correlationId), "Event should contain correlationId");

        consumer.close();
        logger.info("Scenario 1: SUCCESS");
    }

    @Test
    @Order(2)
    @DisplayName("Scenario 2: Processed Event Service receives messages")
    void scenario2_processedEventServiceReceivesMessages() throws Exception {
        assumeNotCI();

        String processedEventName = "bdd_test_2_" + TEST_RUN_ID;
        String rawEventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        logger.info("Scenario 2: Creating processed event with name={}", processedEventName);

        // Create Kafka consumer for processed events
        KafkaConsumer<String, String> consumer = createKafkaConsumer("bdd-test-2-" + System.currentTimeMillis());
        consumer.subscribe(List.of(PROCESSED_TOPIC));

        // WHEN: I post a processed event directly to the API
        String processedEventJson = mapper.writeValueAsString(new ProcessedEventPayload(
                UUID.randomUUID().toString(),
                rawEventId,
                processedEventName,
                correlationId,
                "{\"test\": \"scenario_2\", \"enriched\": true}",
                Instant.now().toString(),
                42.0,
                "SUCCESS"
        ));

        given()
                .contentType(ContentType.JSON)
                .body(processedEventJson)
                .when()
                .post("/processedevent/api")
                .then()
                .statusCode(201);

        logger.info("Scenario 2: Processed event created, waiting for Kafka message...");

        // THEN: It should appear in Kafka via CDC
        String receivedEvent = waitForKafkaMessage(consumer, correlationId, Duration.ofSeconds(30));

        assertNotNull(receivedEvent, "Should receive processed event from Kafka");
        assertTrue(receivedEvent.contains(processedEventName), "Event should contain the correct name");
        assertTrue(receivedEvent.contains("SUCCESS"), "Event should have SUCCESS status");

        consumer.close();
        logger.info("Scenario 2: SUCCESS");
    }

    @Test
    @Order(3)
    @DisplayName("Scenario 3: Processor consumes from Kafka and calls API")
    void scenario3_processorConsumesAndCallsApi() throws Exception {
        assumeNotCI();

        String eventName = "bdd_test_3_" + TEST_RUN_ID;
        String correlationId = UUID.randomUUID().toString();

        logger.info("Scenario 3: Creating raw event for processor with name={}", eventName);

        // Create Kafka consumer for processed events
        KafkaConsumer<String, String> consumer = createKafkaConsumer("bdd-test-3-" + System.currentTimeMillis());
        consumer.subscribe(List.of(PROCESSED_TOPIC));

        // WHEN: I post a raw event (which the processor should pick up)
        String eventJson = mapper.writeValueAsString(new EventPayload(
                eventName,
                correlationId,
                "{\"test\": \"scenario_3\", \"user_id\": \"user999\"}",
                Instant.now().toString(),
                "bdd_test",
                "1.0"
        ));

        given()
                .contentType(ContentType.JSON)
                .body(eventJson)
                .when()
                .post("/event/api")
                .then()
                .statusCode(201);

        logger.info("Scenario 3: Raw event created, waiting for processed event in Kafka...");

        // THEN: The processor should consume it and create a processed event
        String receivedEvent = waitForKafkaMessage(consumer, eventName, Duration.ofSeconds(45));

        assertNotNull(receivedEvent, "Should receive processed event from Kafka");
        assertTrue(receivedEvent.contains(eventName), "Processed event should have correct name");
        assertTrue(receivedEvent.contains("SUCCESS"), "Processed event should have SUCCESS status");

        consumer.close();
        logger.info("Scenario 3: SUCCESS");
    }

    @Test
    @Order(4)
    @DisplayName("Scenario 4: Full End-to-End Flow")
    void scenario4_fullEndToEndFlow() throws Exception {
        assumeNotCI();

        String eventName = "bdd_test_4_" + TEST_RUN_ID;
        String correlationId = UUID.randomUUID().toString();

        logger.info("Scenario 4: Starting full E2E test with name={}, correlationId={}", eventName, correlationId);

        // Create Kafka consumer for processed events
        KafkaConsumer<String, String> consumer = createKafkaConsumer("bdd-test-4-" + System.currentTimeMillis());
        consumer.subscribe(List.of(PROCESSED_TOPIC));

        // WHEN: I post a raw event
        String eventJson = mapper.writeValueAsString(new EventPayload(
                eventName,
                correlationId,
                "{\"test\": \"scenario_4_full_e2e\", \"user_id\": \"user_e2e\", \"username\": \"test_user\", \"action\": \"login\"}",
                Instant.now().toString(),
                "bdd_test_e2e",
                "1.0"
        ));

        String response = given()
                .contentType(ContentType.JSON)
                .body(eventJson)
                .when()
                .post("/event/api")
                .then()
                .statusCode(201)
                .extract()
                .asString();

        logger.info("Scenario 4: Raw event created, waiting for full pipeline...");

        // THEN: The full pipeline should work end-to-end
        String receivedEvent = waitForKafkaMessage(consumer, eventName, Duration.ofSeconds(45));

        assertNotNull(receivedEvent, "Should receive processed event from Kafka");

        // Parse and verify the processed event
        JsonNode envelope = mapper.readTree(receivedEvent);
        String afterString = envelope.path("payload").path("after").asText();
        JsonNode afterDoc = mapper.readTree(afterString);
        JsonNode eventData = afterDoc.has("payload") ? afterDoc.get("payload") : afterDoc;

        assertEquals(eventName, eventData.path("name").asText(), "Name should match");
        assertEquals("SUCCESS", eventData.path("status").asText(), "Status should be SUCCESS");

        // Verify enrichment happened
        String processedDataStr = eventData.path("processed_data").asText();
        JsonNode processedData = mapper.readTree(processedDataStr);
        assertEquals("user_e2e", processedData.path("user_id").asText(), "user_id should be preserved");
        assertEquals("test_user", processedData.path("username").asText(), "username should be preserved");
        assertTrue(processedData.path("enriched").asBoolean(), "Event should be enriched");

        consumer.close();
        logger.info("Scenario 4: SUCCESS - Full E2E flow completed");
    }

    // Helper methods

    private void assumeNotCI() {
        if ("true".equals(System.getenv("CI"))) {
            throw new org.opentest4j.TestAbortedException("Skipping in CI environment");
        }
    }

    private KafkaConsumer<String, String> createKafkaConsumer(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return new KafkaConsumer<>(props);
    }

    private String waitForKafkaMessage(KafkaConsumer<String, String> consumer, String searchString, Duration timeout) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeout.toMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

            for (ConsumerRecord<String, String> record : records) {
                String value = record.value();
                if (value != null && value.contains(searchString)) {
                    logger.info("Found message containing '{}' after {}ms",
                            searchString, System.currentTimeMillis() - startTime);
                    return value;
                }
            }
        }

        logger.error("Timeout waiting for message containing '{}'", searchString);
        return null;
    }

    // Payload record classes

    record EventPayload(
            String name,
            String correlationId,
            String data,
            String timestamp,
            String source,
            String version
    ) {}

    record ProcessedEventPayload(
            String id,
            String raw_event_id,
            String name,
            String correlationId,
            String processed_data,
            String processed_timestamp,
            Double processing_time_ms,
            String status
    ) {}
}
