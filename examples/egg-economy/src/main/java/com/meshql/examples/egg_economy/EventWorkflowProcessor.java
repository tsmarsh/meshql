package com.meshql.examples.egg_economy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumes 5 Debezium CDC topics from Kafka and routes events to projection updaters.
 *
 * Topic routing:
 *   {db}.lay_report           → HenProductivityUpdater + FarmOutputUpdater
 *   {db}.storage_deposit      → ContainerInventoryUpdater
 *   {db}.storage_withdrawal   → ContainerInventoryUpdater
 *   {db}.container_transfer   → ContainerInventoryUpdater (x2: source + dest)
 *   {db}.consumption_report   → ContainerInventoryUpdater
 */
public class EventWorkflowProcessor {
    private static final Logger logger = LoggerFactory.getLogger(EventWorkflowProcessor.class);

    private final String kafkaBroker;
    private final String eventsDb;
    private final String platformUrl;
    private final ObjectMapper mapper;
    private final AtomicBoolean running;

    private final ContainerInventoryUpdater containerInventoryUpdater;
    private final HenProductivityUpdater henProductivityUpdater;
    private final FarmOutputUpdater farmOutputUpdater;

    private KafkaConsumer<String, String> consumer;
    private Thread consumerThread;

    public EventWorkflowProcessor(String kafkaBroker, String eventsDb, String platformUrl) {
        this.kafkaBroker = kafkaBroker;
        this.eventsDb = eventsDb;
        this.platformUrl = platformUrl;
        this.mapper = new ObjectMapper();
        this.running = new AtomicBoolean(false);

        ProjectionCache cache = new ProjectionCache(platformUrl);
        this.containerInventoryUpdater = new ContainerInventoryUpdater(platformUrl, cache);
        this.henProductivityUpdater = new HenProductivityUpdater(platformUrl, cache);
        this.farmOutputUpdater = new FarmOutputUpdater(platformUrl, cache);
    }

    public void start() {
        if (running.getAndSet(true)) {
            logger.warn("Processor already running");
            return;
        }

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "egg-economy-processor");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        consumer = new KafkaConsumer<>(props);

        // Subscribe to all 5 event topics
        // Debezium topic format: {topic.prefix}.{database}.{collection}
        String prefix = eventsDb + "." + eventsDb;
        consumer.subscribe(List.of(
                prefix + ".lay_report",
                prefix + ".storage_deposit",
                prefix + ".storage_withdrawal",
                prefix + ".container_transfer",
                prefix + ".consumption_report"
        ));

        consumerThread = new Thread(this::consumeLoop, "event-workflow-processor");
        consumerThread.start();

        logger.info("Started event workflow processor for topics: {}.{{lay_report,storage_deposit,storage_withdrawal,container_transfer,consumption_report}}", prefix);
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
        logger.info("Event workflow processor stopped");
    }

    private void consumeLoop() {
        try {
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        processRecord(record);
                    } catch (Exception e) {
                        logger.error("Error processing record from {}: {}", record.topic(), e.getMessage(), e);
                    }
                }
            }
        } catch (org.apache.kafka.common.errors.WakeupException e) {
            if (running.get()) {
                throw e;
            }
        } finally {
            logger.info("Consumer loop ended");
        }
    }

    private void processRecord(ConsumerRecord<String, String> record) throws Exception {
        String value = record.value();
        if (value == null || value.isEmpty()) return;

        JsonNode envelope = mapper.readTree(value);
        JsonNode payload = envelope.has("payload") ? envelope.get("payload") : envelope;

        // Extract the 'after' document from Debezium MongoDB CDC
        String afterString = payload.has("after") ? payload.get("after").asText(null) : null;
        if (afterString == null || afterString.isEmpty() || afterString.equals("null")) {
            return;
        }

        JsonNode afterDoc;
        try {
            afterDoc = mapper.readTree(afterString);
        } catch (JsonProcessingException e) {
            afterDoc = payload.get("after");
            if (afterDoc == null || !afterDoc.isObject()) return;
        }

        // Extract the event data from envelope payload
        JsonNode eventData = afterDoc.has("payload") ? afterDoc.get("payload") : afterDoc;

        String topic = record.topic();
        routeEvent(topic, eventData);
    }

    private void routeEvent(String topic, JsonNode eventData) {
        if (topic.endsWith(".lay_report")) {
            henProductivityUpdater.onLayReport(eventData);
            farmOutputUpdater.onLayReport(eventData);
        } else if (topic.endsWith(".storage_deposit")) {
            containerInventoryUpdater.onStorageDeposit(eventData);
        } else if (topic.endsWith(".storage_withdrawal")) {
            containerInventoryUpdater.onStorageWithdrawal(eventData);
        } else if (topic.endsWith(".container_transfer")) {
            String sourceId = eventData.path("source_container_id").asText(null);
            String destId = eventData.path("dest_container_id").asText(null);
            int eggs = eventData.path("eggs").asInt(0);
            if (sourceId != null) containerInventoryUpdater.onContainerTransferSource(sourceId, eggs);
            if (destId != null) containerInventoryUpdater.onContainerTransferDest(destId, eggs);
        } else if (topic.endsWith(".consumption_report")) {
            containerInventoryUpdater.onConsumption(eventData);
        } else {
            logger.warn("Unknown topic: {}", topic);
        }
    }
}
