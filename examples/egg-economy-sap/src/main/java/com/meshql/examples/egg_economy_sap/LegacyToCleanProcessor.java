package com.meshql.examples.egg_economy_sap;

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
 * 5-phase Kafka consumer that processes Debezium CDC messages from the SAP legacy
 * PostgreSQL database, transforms them into clean domain objects via MeshQL REST API,
 * and updates projections inline.
 *
 * Phase 1: Farm, Container, Consumer (roots — no FK dependencies)
 * Phase 2: Coop (depends on Farm)
 * Phase 3: Hen (depends on Coop)
 * Phase 4: All 5 event topics (depend on all actors) + inline projection updates
 * Phase 5: Continuous consumption of all 10 topics
 */
public class LegacyToCleanProcessor {
    private static final Logger logger = LoggerFactory.getLogger(LegacyToCleanProcessor.class);

    private static final String TOPIC_PREFIX = "sap.public.";

    private static final Map<String, String> TOPIC_TO_API = Map.ofEntries(
            Map.entry(TOPIC_PREFIX + "zfarm_mstr", "/farm/api"),
            Map.entry(TOPIC_PREFIX + "zstall_mstr", "/coop/api"),
            Map.entry(TOPIC_PREFIX + "zequi_hen", "/hen/api"),
            Map.entry(TOPIC_PREFIX + "zbehaelt_mstr", "/container/api"),
            Map.entry(TOPIC_PREFIX + "zkunde_vbr", "/consumer/api"),
            Map.entry(TOPIC_PREFIX + "zafru_lege", "/lay_report/api"),
            Map.entry(TOPIC_PREFIX + "zmseg_101", "/storage_deposit/api"),
            Map.entry(TOPIC_PREFIX + "zmseg_201", "/storage_withdrawal/api"),
            Map.entry(TOPIC_PREFIX + "zmseg_301", "/container_transfer/api"),
            Map.entry(TOPIC_PREFIX + "zmseg_261", "/consumption_report/api")
    );

    private final String kafkaBroker;
    private final String platformUrl;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final AtomicBoolean running;
    private final IdResolver idResolver;

    private final FarmTransformer farmTransformer;
    private final CoopTransformer coopTransformer;
    private final HenTransformer henTransformer;
    private final ContainerTransformer containerTransformer;
    private final ConsumerTransformer consumerTransformer;
    private final LayReportTransformer layReportTransformer;
    private final StorageDepositTransformer storageDepositTransformer;
    private final StorageWithdrawalTransformer storageWithdrawalTransformer;
    private final ContainerTransferTransformer containerTransferTransformer;
    private final ConsumptionReportTransformer consumptionReportTransformer;

    private final ContainerInventoryUpdater containerInventoryUpdater;
    private final HenProductivityUpdater henProductivityUpdater;
    private final FarmOutputUpdater farmOutputUpdater;

    private KafkaConsumer<String, String> consumer;
    private Thread consumerThread;

    public LegacyToCleanProcessor(String kafkaBroker, String platformUrl, IdResolver idResolver) {
        this.kafkaBroker = kafkaBroker;
        this.platformUrl = platformUrl;
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
        this.running = new AtomicBoolean(false);
        this.idResolver = idResolver;

        this.farmTransformer = new FarmTransformer();
        this.containerTransformer = new ContainerTransformer();
        this.consumerTransformer = new ConsumerTransformer();
        this.coopTransformer = new CoopTransformer(idResolver);
        this.henTransformer = new HenTransformer(idResolver);
        this.layReportTransformer = new LayReportTransformer(idResolver);
        this.storageDepositTransformer = new StorageDepositTransformer(idResolver);
        this.storageWithdrawalTransformer = new StorageWithdrawalTransformer(idResolver);
        this.containerTransferTransformer = new ContainerTransferTransformer(idResolver);
        this.consumptionReportTransformer = new ConsumptionReportTransformer(idResolver);

        ProjectionCache projectionCache = new ProjectionCache(platformUrl);
        this.containerInventoryUpdater = new ContainerInventoryUpdater(platformUrl, projectionCache);
        this.henProductivityUpdater = new HenProductivityUpdater(platformUrl, projectionCache);
        this.farmOutputUpdater = new FarmOutputUpdater(platformUrl, projectionCache);
    }

    public void start() {
        if (running.getAndSet(true)) {
            logger.warn("Processor already running");
            return;
        }

        consumerThread = new Thread(this::consumeLoop, "sap-legacy-processor");
        consumerThread.start();

        logger.info("Started SAP legacy processor");
    }

    private Properties consumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "egg-economy-sap-processor");
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
            // Phase 1: Farm, Container, Consumer (roots — no FK deps)
            logger.info("Phase 1: Processing farms, containers, consumers...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of(
                    TOPIC_PREFIX + "zfarm_mstr",
                    TOPIC_PREFIX + "zbehaelt_mstr",
                    TOPIC_PREFIX + "zkunde_vbr"
            ));
            drainTopic(consumer);
            consumer.commitSync();
            consumer.close();
            idResolver.populateFarmCacheFromGraphQL();
            idResolver.populateContainerCacheFromGraphQL();
            idResolver.populateConsumerCacheFromGraphQL();
            logger.info("Phase 1 complete: {} farms, {} containers, {} consumers cached",
                    idResolver.farmCacheSize(), idResolver.containerCacheSize(), idResolver.consumerCacheSize());

            // Phase 2: Coop (depends on Farm)
            logger.info("Phase 2: Processing coops...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of(TOPIC_PREFIX + "zstall_mstr"));
            drainTopic(consumer);
            consumer.commitSync();
            consumer.close();
            idResolver.populateCoopCacheFromGraphQL();
            logger.info("Phase 2 complete: {} coops cached", idResolver.coopCacheSize());

            // Phase 3: Hen (depends on Coop)
            logger.info("Phase 3: Processing hens...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of(TOPIC_PREFIX + "zequi_hen"));
            drainTopic(consumer);
            consumer.commitSync();
            consumer.close();
            idResolver.populateHenCacheFromGraphQL();
            logger.info("Phase 3 complete: {} hens cached", idResolver.henCacheSize());

            // Phase 4: All 5 event topics (depend on all actors) + inline projections
            logger.info("Phase 4: Processing events...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of(
                    TOPIC_PREFIX + "zafru_lege",
                    TOPIC_PREFIX + "zmseg_101",
                    TOPIC_PREFIX + "zmseg_201",
                    TOPIC_PREFIX + "zmseg_301",
                    TOPIC_PREFIX + "zmseg_261"
            ));
            drainTopic(consumer);
            consumer.commitSync();
            consumer.close();
            logger.info("Phase 4 complete");

            // Phase 5: Continuous consumption of all 10 topics
            logger.info("Phase 5: Continuous consumption of all topics...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of(
                    TOPIC_PREFIX + "zfarm_mstr",
                    TOPIC_PREFIX + "zstall_mstr",
                    TOPIC_PREFIX + "zequi_hen",
                    TOPIC_PREFIX + "zbehaelt_mstr",
                    TOPIC_PREFIX + "zkunde_vbr",
                    TOPIC_PREFIX + "zafru_lege",
                    TOPIC_PREFIX + "zmseg_101",
                    TOPIC_PREFIX + "zmseg_201",
                    TOPIC_PREFIX + "zmseg_301",
                    TOPIC_PREFIX + "zmseg_261"
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

        JsonNode after = payload.get("after");
        if (after == null || after.isNull()) return;

        // PostgreSQL Debezium: 'after' is already a JSON object
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
            registerIdMapping(topic, after, meshqlId, cleanData);
        }

        // Inline projection updates for event topics
        routeEventToProjections(topic, cleanData);
    }

    private LegacyTransformer getTransformer(String topic) {
        if (topic.endsWith("zfarm_mstr")) return farmTransformer;
        if (topic.endsWith("zstall_mstr")) return coopTransformer;
        if (topic.endsWith("zequi_hen")) return henTransformer;
        if (topic.endsWith("zbehaelt_mstr")) return containerTransformer;
        if (topic.endsWith("zkunde_vbr")) return consumerTransformer;
        if (topic.endsWith("zafru_lege")) return layReportTransformer;
        if (topic.endsWith("zmseg_101")) return storageDepositTransformer;
        if (topic.endsWith("zmseg_201")) return storageWithdrawalTransformer;
        if (topic.endsWith("zmseg_301")) return containerTransferTransformer;
        if (topic.endsWith("zmseg_261")) return consumptionReportTransformer;
        return null;
    }

    private void registerIdMapping(String topic, JsonNode after, String meshqlId, ObjectNode cleanData) {
        if (topic.endsWith("zfarm_mstr")) {
            String legacyId = after.has("werks") ? after.get("werks").asText() : null;
            idResolver.registerFarm(legacyId, meshqlId);
        } else if (topic.endsWith("zstall_mstr")) {
            String legacyId = after.has("stall_nr") ? after.get("stall_nr").asText() : null;
            idResolver.registerCoop(legacyId, meshqlId);
        } else if (topic.endsWith("zequi_hen")) {
            String legacyId = after.has("equnr") ? after.get("equnr").asText() : null;
            String coopId = cleanData.has("coop_id") ? cleanData.get("coop_id").asText() : null;
            idResolver.registerHen(legacyId, meshqlId, coopId);
        } else if (topic.endsWith("zbehaelt_mstr")) {
            String legacyId = after.has("behaelt_nr") ? after.get("behaelt_nr").asText() : null;
            idResolver.registerContainer(legacyId, meshqlId);
        } else if (topic.endsWith("zkunde_vbr")) {
            String legacyId = after.has("kunnr") ? after.get("kunnr").asText() : null;
            idResolver.registerConsumer(legacyId, meshqlId);
        }
    }

    private void routeEventToProjections(String topic, ObjectNode cleanData) {
        if (topic.endsWith("zafru_lege")) {
            henProductivityUpdater.onLayReport(cleanData);
            farmOutputUpdater.onLayReport(cleanData);
        } else if (topic.endsWith("zmseg_101")) {
            containerInventoryUpdater.onStorageDeposit(cleanData);
        } else if (topic.endsWith("zmseg_201")) {
            containerInventoryUpdater.onStorageWithdrawal(cleanData);
        } else if (topic.endsWith("zmseg_301")) {
            String sourceId = cleanData.path("source_container_id").asText(null);
            String destId = cleanData.path("dest_container_id").asText(null);
            int eggs = cleanData.path("eggs").asInt(0);
            if (sourceId != null) containerInventoryUpdater.onContainerTransferSource(sourceId, eggs);
            if (destId != null) containerInventoryUpdater.onContainerTransferDest(destId, eggs);
        } else if (topic.endsWith("zmseg_261")) {
            containerInventoryUpdater.onConsumption(cleanData);
        }
    }

    private String postToApi(String apiPath, ObjectNode data) throws Exception {
        String body = mapper.writeValueAsString(data);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(platformUrl + apiPath))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpClient noRedirectClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpResponse<String> response = noRedirectClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 400) {
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
