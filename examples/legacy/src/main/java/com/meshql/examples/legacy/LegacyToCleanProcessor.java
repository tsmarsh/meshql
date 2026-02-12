package com.meshql.examples.legacy;

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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka consumer that processes Debezium CDC messages from the legacy PostgreSQL
 * database and transforms them into clean domain objects via the MeshQL REST API.
 *
 * Routes messages by topic:
 *   legacy.public.cust_acct  -> CustomerTransformer  -> POST /customer/api
 *   legacy.public.mtr_rdng   -> MeterReadingTransformer -> POST /meter_reading/api
 *   legacy.public.bill_hdr   -> BillTransformer      -> POST /bill/api
 *   legacy.public.pymt_hist  -> PaymentTransformer    -> POST /payment/api
 */
public class LegacyToCleanProcessor {
    private static final Logger logger = LoggerFactory.getLogger(LegacyToCleanProcessor.class);

    private static final Map<String, String> TOPIC_TO_API = Map.of(
            "legacy.public.cust_acct", "/customer/api",
            "legacy.public.mtr_rdng", "/meter_reading/api",
            "legacy.public.bill_hdr", "/bill/api",
            "legacy.public.pymt_hist", "/payment/api"
    );

    private final String kafkaBroker;
    private final String platformUrl;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final AtomicBoolean running;
    private final IdResolver idResolver;

    private final CustomerTransformer customerTransformer;
    private final MeterReadingTransformer meterReadingTransformer;
    private final BillTransformer billTransformer;
    private final PaymentTransformer paymentTransformer;

    private KafkaConsumer<String, String> consumer;
    private Thread consumerThread;

    public LegacyToCleanProcessor(String kafkaBroker, String platformUrl, IdResolver idResolver) {
        this.kafkaBroker = kafkaBroker;
        this.platformUrl = platformUrl;
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
        this.running = new AtomicBoolean(false);
        this.idResolver = idResolver;

        this.customerTransformer = new CustomerTransformer();
        this.meterReadingTransformer = new MeterReadingTransformer(idResolver);
        this.billTransformer = new BillTransformer(idResolver);
        this.paymentTransformer = new PaymentTransformer(idResolver);
    }

    public void start() {
        if (running.getAndSet(true)) {
            logger.warn("Processor already running");
            return;
        }

        consumerThread = new Thread(this::consumeLoop, "legacy-processor");
        consumerThread.start();

        logger.info("Started legacy processor");
    }

    private Properties consumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "legacy-processor");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return props;
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
            // Phase 1: Process customers first (ensures FK resolution cache is populated)
            logger.info("Phase 1: Processing customers...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of("legacy.public.cust_acct"));
            drainTopic(consumer);
            consumer.commitSync();
            consumer.close();
            // REST API doesn't return entity IDs — query GraphQL to populate the cache
            idResolver.populateCustomerCacheFromGraphQL();
            logger.info("Phase 1 complete: {} customers cached", idResolver.customerCacheSize());

            // Phase 2: Process bills (needed for payment FK resolution)
            logger.info("Phase 2: Processing bills...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of("legacy.public.bill_hdr"));
            drainTopic(consumer);
            consumer.commitSync();
            consumer.close();
            // Populate bill cache from GraphQL
            idResolver.populateBillCacheFromGraphQL();
            logger.info("Phase 2 complete: {} bills cached", idResolver.billCacheSize());

            // Phase 3: Process meter readings and payments
            logger.info("Phase 3: Processing meter readings and payments...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of("legacy.public.mtr_rdng", "legacy.public.pymt_hist"));
            drainTopic(consumer);
            consumer.commitSync();
            consumer.close();
            logger.info("Phase 3 complete");

            // Phase 4: Continuous consumption of all topics for ongoing changes
            logger.info("Phase 4: Continuous consumption of all topics...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of(
                    "legacy.public.cust_acct",
                    "legacy.public.mtr_rdng",
                    "legacy.public.bill_hdr",
                    "legacy.public.pymt_hist"
            ));

            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
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

    private void drainTopic(KafkaConsumer<String, String> topicConsumer) {
        int emptyPolls = 0;
        while (running.get() && emptyPolls < 10) {
            ConsumerRecords<String, String> records = topicConsumer.poll(Duration.ofMillis(500));
            if (records.isEmpty()) {
                emptyPolls++;
            } else {
                emptyPolls = 0;
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        processRecord(record);
                    } catch (Exception e) {
                        logger.error("Error processing record from {}: {}", record.topic(), e.getMessage(), e);
                    }
                }
            }
        }
    }

    private void processRecord(ConsumerRecord<String, String> record) throws Exception {
        String value = record.value();
        if (value == null || value.isEmpty()) return;

        JsonNode envelope = mapper.readTree(value);
        JsonNode payload = envelope.has("payload") ? envelope.get("payload") : envelope;

        // Extract the 'after' document — PostgreSQL CDC provides a proper JSON object
        JsonNode after = payload.get("after");
        if (after == null || after.isNull()) {
            logger.debug("No 'after' document (delete operation?), skipping");
            return;
        }

        // PostgreSQL Debezium: 'after' is already a JSON object (not double-encoded)
        if (after.isTextual()) {
            after = mapper.readTree(after.asText());
        }

        String topic = record.topic();
        LegacyTransformer transformer = getTransformer(topic);
        if (transformer == null) {
            logger.warn("No transformer for topic: {}", topic);
            return;
        }

        ObjectNode cleanData = transformer.transform(after);
        String apiPath = TOPIC_TO_API.get(topic);
        if (apiPath == null) return;

        String meshqlId = postToApi(apiPath, cleanData);

        // Register ID mappings for FK resolution
        if (meshqlId != null) {
            if (topic.endsWith("cust_acct")) {
                String legacyId = after.has("acct_id") ? after.get("acct_id").asText() : null;
                idResolver.registerCustomer(legacyId, meshqlId);
            } else if (topic.endsWith("bill_hdr")) {
                String legacyId = after.has("bill_id") ? after.get("bill_id").asText() : null;
                idResolver.registerBill(legacyId, meshqlId);
            }
        }
    }

    private LegacyTransformer getTransformer(String topic) {
        if (topic.endsWith("cust_acct")) return customerTransformer;
        if (topic.endsWith("mtr_rdng")) return meterReadingTransformer;
        if (topic.endsWith("bill_hdr")) return billTransformer;
        if (topic.endsWith("pymt_hist")) return paymentTransformer;
        return null;
    }

    /**
     * POST the transformed data to the MeshQL REST API.
     * Returns the MeshQL entity ID from the Location header redirect, or null.
     */
    private String postToApi(String apiPath, ObjectNode data) throws Exception {
        String body = mapper.writeValueAsString(data);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(platformUrl + apiPath))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        // Don't follow redirects — we need the Location header
        HttpClient noRedirectClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpResponse<String> response = noRedirectClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 400) {
            // Extract ID from Location header (e.g., /customer/api/abc-123)
            String location = response.headers().firstValue("Location").orElse(null);
            if (location != null) {
                String id = location.substring(location.lastIndexOf('/') + 1);
                logger.info("POST {} -> {} (id: {})", apiPath, response.statusCode(), id);
                return id;
            }
            logger.info("POST {} -> {}", apiPath, response.statusCode());
            return null;
        } else {
            logger.error("POST {} failed: {} - {}", apiPath, response.statusCode(), response.body());
            return null;
        }
    }
}
