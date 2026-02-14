package com.meshql.examples.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
 * 5-phase Kafka consumer that processes Debezium CDC messages from BOTH
 * SAP PostgreSQL (4 tables) and Distribution PostgreSQL (6 tables),
 * transforms them into clean domain objects via MeshQL REST API,
 * updates projections inline, and publishes to clean Kafka topics for the LakeWriter.
 *
 * Phase 1: Farm (SAP root) + Container, Consumer (distro roots — no FK deps)
 * Phase 2: Coop (depends on Farm)
 * Phase 3: Hen (depends on Coop)
 * Phase 4: All event topics (SAP: lay_report; distro: storage_deposit, withdrawal, transfer, consumption)
 * Phase 5: Continuous consumption of all 10 topics
 */
public class EnterpriseLegacyProcessor {
    private static final Logger logger = LoggerFactory.getLogger(EnterpriseLegacyProcessor.class);

    // SAP topic prefix (Debezium: sap.public.<table>)
    private static final String SAP_PREFIX = "sap.public.";
    // Distro topic prefix (Debezium: distro.public.<table>)
    private static final String DISTRO_PREFIX = "distro.public.";

    private static final Map<String, String> TOPIC_TO_API = Map.ofEntries(
            // SAP topics (4)
            Map.entry(SAP_PREFIX + "zfarm_mstr", "/farm/api"),
            Map.entry(SAP_PREFIX + "zstall_mstr", "/coop/api"),
            Map.entry(SAP_PREFIX + "zequi_hen", "/hen/api"),
            Map.entry(SAP_PREFIX + "zafru_lege", "/lay_report/api"),
            // Distro topics (6)
            Map.entry(DISTRO_PREFIX + "containers", "/container/api"),
            Map.entry(DISTRO_PREFIX + "customers", "/consumer/api"),
            Map.entry(DISTRO_PREFIX + "storage_deposits", "/storage_deposit/api"),
            Map.entry(DISTRO_PREFIX + "storage_withdrawals", "/storage_withdrawal/api"),
            Map.entry(DISTRO_PREFIX + "container_transfers", "/container_transfer/api"),
            Map.entry(DISTRO_PREFIX + "consumption_reports", "/consumption_report/api")
    );

    private static final Map<String, String> TOPIC_TO_CLEAN_TOPIC = Map.ofEntries(
            Map.entry(SAP_PREFIX + "zfarm_mstr", "clean.farm"),
            Map.entry(SAP_PREFIX + "zstall_mstr", "clean.coop"),
            Map.entry(SAP_PREFIX + "zequi_hen", "clean.hen"),
            Map.entry(SAP_PREFIX + "zafru_lege", "clean.lay_report"),
            Map.entry(DISTRO_PREFIX + "containers", "clean.container"),
            Map.entry(DISTRO_PREFIX + "customers", "clean.consumer"),
            Map.entry(DISTRO_PREFIX + "storage_deposits", "clean.storage_deposit"),
            Map.entry(DISTRO_PREFIX + "storage_withdrawals", "clean.storage_withdrawal"),
            Map.entry(DISTRO_PREFIX + "container_transfers", "clean.container_transfer"),
            Map.entry(DISTRO_PREFIX + "consumption_reports", "clean.consumption_report")
    );

    private final String kafkaBroker;
    private final String platformUrl;
    private final ObjectMapper mapper;
    private final AtomicBoolean running;
    private final IdResolver idResolver;

    // SAP transformers
    private final FarmTransformer farmTransformer;
    private final CoopTransformer coopTransformer;
    private final HenTransformer henTransformer;
    private final LayReportTransformer layReportTransformer;

    // Distro transformers
    private final DistroContainerTransformer distroContainerTransformer;
    private final DistroConsumerTransformer distroConsumerTransformer;
    private final DistroStorageDepositTransformer distroStorageDepositTransformer;
    private final DistroStorageWithdrawalTransformer distroStorageWithdrawalTransformer;
    private final DistroContainerTransferTransformer distroContainerTransferTransformer;
    private final DistroConsumptionReportTransformer distroConsumptionReportTransformer;

    // Projection updaters
    private final ContainerInventoryUpdater containerInventoryUpdater;
    private final HenProductivityUpdater henProductivityUpdater;
    private final FarmOutputUpdater farmOutputUpdater;

    private KafkaConsumer<String, String> consumer;
    private KafkaProducer<String, String> producer;
    private Thread consumerThread;

    public EnterpriseLegacyProcessor(String kafkaBroker, String platformUrl, IdResolver idResolver) {
        this.kafkaBroker = kafkaBroker;
        this.platformUrl = platformUrl;
        this.mapper = new ObjectMapper();
        this.running = new AtomicBoolean(false);
        this.idResolver = idResolver;

        // SAP transformers
        this.farmTransformer = new FarmTransformer();
        this.coopTransformer = new CoopTransformer(idResolver);
        this.henTransformer = new HenTransformer(idResolver);
        this.layReportTransformer = new LayReportTransformer(idResolver);

        // Distro transformers
        this.distroContainerTransformer = new DistroContainerTransformer();
        this.distroConsumerTransformer = new DistroConsumerTransformer();
        this.distroStorageDepositTransformer = new DistroStorageDepositTransformer(idResolver);
        this.distroStorageWithdrawalTransformer = new DistroStorageWithdrawalTransformer(idResolver);
        this.distroContainerTransferTransformer = new DistroContainerTransferTransformer(idResolver);
        this.distroConsumptionReportTransformer = new DistroConsumptionReportTransformer(idResolver);

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

        // Initialize Kafka producer for clean topics
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        this.producer = new KafkaProducer<>(producerProps);

        consumerThread = new Thread(this::consumeLoop, "enterprise-legacy-processor");
        consumerThread.start();

        logger.info("Started enterprise legacy processor");
    }

    private Properties consumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "enterprise-processor");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return props;
    }

    public void stop() {
        running.set(false);
        if (consumer != null) consumer.wakeup();
        if (consumerThread != null) {
            try { consumerThread.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (consumer != null) consumer.close();
        if (producer != null) producer.close();
        logger.info("Processor stopped");
    }

    private void consumeLoop() {
        try {
            // Phase 1: Farm (SAP) + Container, Consumer (distro) — roots, no FK deps
            logger.info("Phase 1: Processing farms, containers, consumers...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of(
                    SAP_PREFIX + "zfarm_mstr",
                    DISTRO_PREFIX + "containers",
                    DISTRO_PREFIX + "customers"
            ));
            drainTopic(consumer);
            consumer.commitSync();
            consumer.close();
            idResolver.populateFarmCacheFromGraphQL();
            idResolver.populateDistroContainerCacheFromGraphQL();
            idResolver.populateDistroConsumerCacheFromGraphQL();
            logger.info("Phase 1 complete: {} farms, {} containers, {} consumers cached",
                    idResolver.farmCacheSize(), idResolver.distroContainerCacheSize(), idResolver.distroConsumerCacheSize());

            // Phase 2: Coop (depends on Farm)
            logger.info("Phase 2: Processing coops...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of(SAP_PREFIX + "zstall_mstr"));
            drainTopic(consumer);
            consumer.commitSync();
            consumer.close();
            idResolver.populateCoopCacheFromGraphQL();
            logger.info("Phase 2 complete: {} coops cached", idResolver.coopCacheSize());

            // Phase 3: Hen (depends on Coop)
            logger.info("Phase 3: Processing hens...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of(SAP_PREFIX + "zequi_hen"));
            drainTopic(consumer);
            consumer.commitSync();
            consumer.close();
            idResolver.populateHenCacheFromGraphQL();
            logger.info("Phase 3 complete: {} hens cached", idResolver.henCacheSize());

            // Phase 4: All event topics
            logger.info("Phase 4: Processing events...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of(
                    SAP_PREFIX + "zafru_lege",
                    DISTRO_PREFIX + "storage_deposits",
                    DISTRO_PREFIX + "storage_withdrawals",
                    DISTRO_PREFIX + "container_transfers",
                    DISTRO_PREFIX + "consumption_reports"
            ));
            drainTopic(consumer);
            consumer.commitSync();
            consumer.close();
            logger.info("Phase 4 complete");

            // Phase 5: Continuous consumption of all 10 topics
            logger.info("Phase 5: Continuous consumption of all topics...");
            consumer = new KafkaConsumer<>(consumerProps());
            consumer.subscribe(List.of(
                    SAP_PREFIX + "zfarm_mstr",
                    SAP_PREFIX + "zstall_mstr",
                    SAP_PREFIX + "zequi_hen",
                    SAP_PREFIX + "zafru_lege",
                    DISTRO_PREFIX + "containers",
                    DISTRO_PREFIX + "customers",
                    DISTRO_PREFIX + "storage_deposits",
                    DISTRO_PREFIX + "storage_withdrawals",
                    DISTRO_PREFIX + "container_transfers",
                    DISTRO_PREFIX + "consumption_reports"
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
            if (running.get()) throw e;
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

        // Dedup: check if this legacy ID already exists (write-back round-trip)
        if (isDuplicate(topic, after, cleanData)) {
            logger.debug("Skipping duplicate from topic {}", topic);
            return;
        }

        String meshqlId = postToApi(apiPath, cleanData);

        if (meshqlId != null) {
            registerIdMapping(topic, after, meshqlId, cleanData);
        }

        // Publish to clean Kafka topic for LakeWriter
        String cleanTopic = TOPIC_TO_CLEAN_TOPIC.get(topic);
        if (cleanTopic != null) {
            producer.send(new ProducerRecord<>(cleanTopic, mapper.writeValueAsString(cleanData)));
        }

        routeEventToProjections(topic, cleanData);
    }

    private boolean isDuplicate(String topic, JsonNode after, ObjectNode cleanData) {
        // For SAP actor entities, check legacy field in cache
        if (topic.endsWith("zfarm_mstr")) {
            String werks = after.path("werks").asText(null);
            return idResolver.hasFarmMapping(werks);
        }
        // For distro actor entities, check legacy_distro_id in cache
        if (topic.equals(DISTRO_PREFIX + "containers")) {
            String distroId = String.valueOf(after.path("id").asInt());
            return idResolver.hasDistroContainerMapping(distroId);
        }
        if (topic.equals(DISTRO_PREFIX + "customers")) {
            String distroId = String.valueOf(after.path("id").asInt());
            return idResolver.hasDistroConsumerMapping(distroId);
        }
        // Events are never deduped (idempotent by design)
        return false;
    }

    private LegacyTransformer getTransformer(String topic) {
        // SAP
        if (topic.endsWith("zfarm_mstr")) return farmTransformer;
        if (topic.endsWith("zstall_mstr")) return coopTransformer;
        if (topic.endsWith("zequi_hen")) return henTransformer;
        if (topic.endsWith("zafru_lege")) return layReportTransformer;
        // Distro
        if (topic.endsWith("containers")) return distroContainerTransformer;
        if (topic.endsWith("customers")) return distroConsumerTransformer;
        if (topic.endsWith("storage_deposits")) return distroStorageDepositTransformer;
        if (topic.endsWith("storage_withdrawals")) return distroStorageWithdrawalTransformer;
        if (topic.endsWith("container_transfers")) return distroContainerTransferTransformer;
        if (topic.endsWith("consumption_reports")) return distroConsumptionReportTransformer;
        return null;
    }

    private void registerIdMapping(String topic, JsonNode after, String meshqlId, ObjectNode cleanData) {
        // SAP
        if (topic.endsWith("zfarm_mstr")) {
            idResolver.registerFarm(after.path("werks").asText(null), meshqlId);
        } else if (topic.endsWith("zstall_mstr")) {
            idResolver.registerCoop(after.path("stall_nr").asText(null), meshqlId);
        } else if (topic.endsWith("zequi_hen")) {
            String coopId = cleanData.path("coop_id").asText(null);
            idResolver.registerHen(after.path("equnr").asText(null), meshqlId, coopId);
        }
        // Distro
        else if (topic.endsWith("containers")) {
            idResolver.registerDistroContainer(String.valueOf(after.path("id").asInt()), meshqlId);
        } else if (topic.endsWith("customers")) {
            idResolver.registerDistroConsumer(String.valueOf(after.path("id").asInt()), meshqlId);
        }
    }

    private void routeEventToProjections(String topic, ObjectNode cleanData) {
        // SAP events
        if (topic.endsWith("zafru_lege")) {
            henProductivityUpdater.onLayReport(cleanData);
            farmOutputUpdater.onLayReport(cleanData);
        }
        // Distro events
        else if (topic.endsWith("storage_deposits")) {
            containerInventoryUpdater.onStorageDeposit(cleanData);
        } else if (topic.endsWith("storage_withdrawals")) {
            containerInventoryUpdater.onStorageWithdrawal(cleanData);
        } else if (topic.endsWith("container_transfers")) {
            String sourceId = cleanData.path("source_container_id").asText(null);
            String destId = cleanData.path("dest_container_id").asText(null);
            int eggs = cleanData.path("eggs").asInt(0);
            if (sourceId != null) containerInventoryUpdater.onContainerTransferSource(sourceId, eggs);
            if (destId != null) containerInventoryUpdater.onContainerTransferDest(destId, eggs);
        } else if (topic.endsWith("consumption_reports")) {
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
