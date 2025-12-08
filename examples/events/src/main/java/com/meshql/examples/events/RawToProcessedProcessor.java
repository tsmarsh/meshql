package com.meshql.examples.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka consumer that processes raw events into processed events.
 *
 * This processor:
 * 1. Consumes Debezium CDC messages from the raw events topic
 * 2. Parses the Debezium envelope to extract the document
 * 3. Enriches the data with processing metadata
 * 4. Posts the processed event to the REST API
 */
public class RawToProcessedProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RawToProcessedProcessor.class);

    private final String kafkaBroker;
    private final String rawTopic;
    private final String processedApiBase;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final AtomicBoolean running;

    private KafkaConsumer<String, String> consumer;
    private Thread consumerThread;

    public RawToProcessedProcessor(String kafkaBroker, String rawTopic, String processedApiBase) {
        this.kafkaBroker = kafkaBroker;
        this.rawTopic = rawTopic;
        this.processedApiBase = processedApiBase;
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
        this.running = new AtomicBoolean(false);
    }

    public void start() {
        if (running.getAndSet(true)) {
            logger.warn("Processor already running");
            return;
        }

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker);
        // Use timestamp in group ID for fresh consumer on restart
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "events-processor-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(rawTopic));

        consumerThread = new Thread(this::consumeLoop, "kafka-processor");
        consumerThread.start();

        logger.info("Started Kafka consumer for topic: {}", rawTopic);
    }

    public void stop() {
        running.set(false);
        if (consumer != null) {
            consumer.wakeup();
        }
        if (consumerThread != null) {
            try {
                consumerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (consumer != null) {
            consumer.close();
        }
        logger.info("Processor stopped");
    }

    private void consumeLoop() {
        try {
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        processRecord(record);
                    } catch (Exception e) {
                        logger.error("Error processing record: {}", e.getMessage(), e);
                    }
                }
            }
        } catch (org.apache.kafka.common.errors.WakeupException e) {
            if (running.get()) {
                throw e;
            }
            // Expected during shutdown
        } finally {
            logger.info("Consumer loop ended");
        }
    }

    private void processRecord(ConsumerRecord<String, String> record) throws Exception {
        long startTime = System.currentTimeMillis();
        String value = record.value();

        if (value == null || value.isEmpty()) {
            logger.debug("Skipping empty message");
            return;
        }

        // Parse Debezium envelope
        JsonNode envelope = mapper.readTree(value);

        // Extract the 'after' document from Debezium payload
        JsonNode payload = envelope.get("payload");
        if (payload == null) {
            logger.debug("No payload in message, skipping");
            return;
        }

        String afterString = payload.has("after") ? payload.get("after").asText() : null;
        if (afterString == null || afterString.isEmpty() || afterString.equals("null")) {
            logger.debug("No 'after' document (delete operation?), skipping");
            return;
        }

        // The 'after' field is double-encoded JSON from Debezium
        JsonNode afterDoc;
        try {
            afterDoc = mapper.readTree(afterString);
        } catch (JsonProcessingException e) {
            // Maybe it's already a JSON object
            afterDoc = payload.get("after");
            if (!afterDoc.isObject()) {
                logger.warn("Could not parse 'after' document: {}", afterString);
                return;
            }
        }

        // Extract the raw event ID (prefer UUID, fall back to MongoDB ObjectId)
        String rawEventId = null;
        if (afterDoc.has("id")) {
            rawEventId = afterDoc.get("id").asText();
        } else if (afterDoc.has("_id")) {
            JsonNode idNode = afterDoc.get("_id");
            if (idNode.has("$oid")) {
                rawEventId = idNode.get("$oid").asText();
            } else {
                rawEventId = idNode.asText();
            }
        }

        if (rawEventId == null) {
            logger.warn("No ID found in document, skipping");
            return;
        }

        // Extract the actual event data (handle both API-created and manual inserts)
        JsonNode eventData = afterDoc.has("payload") ? afterDoc.get("payload") : afterDoc;

        // Skip documents without a name field (initialization documents)
        if (!eventData.has("name")) {
            logger.debug("Document has no 'name' field, skipping");
            return;
        }

        // Build the processed event
        ObjectNode processedEvent = buildProcessedEvent(rawEventId, eventData, startTime);

        // Post to the processed events API
        postProcessedEvent(processedEvent);
    }

    private ObjectNode buildProcessedEvent(String rawEventId, JsonNode eventData, long startTime) throws Exception {
        ObjectNode processed = mapper.createObjectNode();

        processed.put("id", UUID.randomUUID().toString());
        processed.put("raw_event_id", rawEventId);
        processed.put("name", eventData.get("name").asText());

        // Copy correlationId if present
        if (eventData.has("correlationId") && !eventData.get("correlationId").isNull()) {
            processed.put("correlationId", eventData.get("correlationId").asText());
        }

        // Enrich the data with processing metadata
        ObjectNode enrichedData = mapper.createObjectNode();

        // Copy original data
        if (eventData.has("data")) {
            String dataStr = eventData.get("data").asText();
            try {
                JsonNode originalData = mapper.readTree(dataStr);
                originalData.fields().forEachRemaining(field ->
                        enrichedData.set(field.getKey(), field.getValue()));
            } catch (JsonProcessingException e) {
                enrichedData.put("raw_data", dataStr);
            }
        }

        // Add enrichment
        enrichedData.put("enriched", true);
        enrichedData.put("processed_at", Instant.now().toString());
        enrichedData.put("processor_version", "1.0.0");

        processed.put("processed_data", mapper.writeValueAsString(enrichedData));
        processed.put("processed_timestamp", Instant.now().toString());
        processed.put("processing_time_ms", System.currentTimeMillis() - startTime);
        processed.put("status", "SUCCESS");

        return processed;
    }

    private void postProcessedEvent(ObjectNode processedEvent) throws Exception {
        String body = mapper.writeValueAsString(processedEvent);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(processedApiBase))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            logger.info("Successfully posted processed event: {} -> {}",
                    processedEvent.get("raw_event_id").asText(),
                    processedEvent.get("id").asText());
        } else {
            logger.error("Failed to post processed event: {} - {}",
                    response.statusCode(), response.body());
        }
    }
}
