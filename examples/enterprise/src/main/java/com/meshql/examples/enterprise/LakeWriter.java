package com.meshql.examples.enterprise;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumes clean Kafka topics and writes JSON lines to MinIO,
 * partitioned by entity type and date: lake/{entity_type}/{yyyy-MM-dd}/{timestamp}.jsonl
 *
 * Batches events and flushes every 10 seconds or 100 events.
 */
public class LakeWriter {
    private static final Logger logger = LoggerFactory.getLogger(LakeWriter.class);

    private static final String BUCKET = "enterprise-lake";
    private static final int BATCH_SIZE = 100;
    private static final long FLUSH_INTERVAL_MS = 10_000;

    private static final List<String> CLEAN_TOPICS = List.of(
            "clean.farm", "clean.coop", "clean.hen", "clean.lay_report",
            "clean.container", "clean.consumer",
            "clean.storage_deposit", "clean.storage_withdrawal",
            "clean.container_transfer", "clean.consumption_report"
    );

    private final String kafkaBroker;
    private final MinioClient minioClient;
    private final ObjectMapper mapper;
    private final AtomicBoolean running;
    private final ConcurrentHashMap<String, List<String>> buffers;
    private long lastFlush;

    private KafkaConsumer<String, String> consumer;
    private Thread writerThread;

    public LakeWriter(String kafkaBroker, String minioEndpoint, String minioAccessKey, String minioSecretKey) {
        this.kafkaBroker = kafkaBroker;
        this.mapper = new ObjectMapper();
        this.running = new AtomicBoolean(false);
        this.buffers = new ConcurrentHashMap<>();
        this.lastFlush = System.currentTimeMillis();

        this.minioClient = MinioClient.builder()
                .endpoint(minioEndpoint)
                .credentials(minioAccessKey, minioSecretKey)
                .build();

        CLEAN_TOPICS.forEach(t -> buffers.put(t, Collections.synchronizedList(new ArrayList<>())));
    }

    public void start() {
        if (running.getAndSet(true)) return;

        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
                logger.info("Created MinIO bucket: {}", BUCKET);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize MinIO bucket: {}", e.getMessage(), e);
        }

        writerThread = new Thread(this::consumeLoop, "lake-writer");
        writerThread.start();
        logger.info("Started lake writer");
    }

    public void stop() {
        running.set(false);
        if (consumer != null) consumer.wakeup();
        if (writerThread != null) {
            try { writerThread.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        flushAll();
        if (consumer != null) consumer.close();
        logger.info("Lake writer stopped");
    }

    private void consumeLoop() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "enterprise-lake-writer");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        try {
            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(CLEAN_TOPICS);

            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    String topic = record.topic();
                    String value = record.value();
                    if (value != null && !value.isEmpty()) {
                        buffers.computeIfAbsent(topic, k -> Collections.synchronizedList(new ArrayList<>())).add(value);
                    }
                }

                // Check if we should flush
                boolean shouldFlush = (System.currentTimeMillis() - lastFlush) >= FLUSH_INTERVAL_MS;
                if (!shouldFlush) {
                    for (List<String> buffer : buffers.values()) {
                        if (buffer.size() >= BATCH_SIZE) {
                            shouldFlush = true;
                            break;
                        }
                    }
                }

                if (shouldFlush) {
                    flushAll();
                }
            }
        } catch (org.apache.kafka.common.errors.WakeupException e) {
            if (running.get()) throw e;
        } finally {
            logger.info("Lake writer loop ended");
        }
    }

    private void flushAll() {
        for (Map.Entry<String, List<String>> entry : buffers.entrySet()) {
            List<String> buffer = entry.getValue();
            if (buffer.isEmpty()) continue;

            List<String> toFlush;
            synchronized (buffer) {
                toFlush = new ArrayList<>(buffer);
                buffer.clear();
            }

            String topic = entry.getKey();
            String entityType = topic.replace("clean.", "");
            flushToMinio(entityType, toFlush);
        }
        lastFlush = System.currentTimeMillis();
    }

    private void flushToMinio(String entityType, List<String> events) {
        try {
            String now = Instant.now().toString();
            String date = now.substring(0, 10); // yyyy-MM-dd
            String timestamp = now.replace(":", "-").replace(".", "-");
            String objectKey = "lake/" + entityType + "/" + date + "/" + timestamp + ".jsonl";

            StringBuilder jsonLines = new StringBuilder();
            for (String event : events) {
                jsonLines.append(event).append("\n");
            }

            byte[] bytes = jsonLines.toString().getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(BUCKET)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType("application/x-ndjson")
                    .build());

            logger.info("Flushed {} events to MinIO: {}", events.size(), objectKey);
        } catch (Exception e) {
            logger.error("Failed to flush {} events for {}: {}", events.size(), entityType, e.getMessage(), e);
        }
    }
}
