package com.meshql.examples.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Transforms distribution system `storage_deposits` rows into clean StorageDeposit entities.
 *
 * Distro columns: id, container_id(FK), source_type, source_id, eggs, recorded_at
 * Clean fields: container_id, source_type, source_id, eggs, timestamp, legacy_distro_id
 */
public class DistroStorageDepositTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(DistroStorageDepositTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final IdResolver idResolver;

    public DistroStorageDepositTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_distro_id", String.valueOf(row.path("id").asInt()));

        // Resolve container FK: distro integer id -> meshql UUID
        String distroContainerId = String.valueOf(row.path("container_id").asInt());
        String containerId = idResolver.resolveDistroContainerId(distroContainerId);
        if (containerId != null) {
            clean.put("container_id", containerId);
        }

        clean.put("source_type", row.path("source_type").asText("farm"));
        clean.put("source_id", row.path("source_id").asText(""));
        clean.put("eggs", row.path("eggs").asInt(0));
        clean.put("timestamp", parseDistroTimestamp(row, "recorded_at"));

        logger.debug("Transformed distro storage deposit: id={}, eggs={}", clean.get("legacy_distro_id"), clean.get("eggs"));
        return clean;
    }

    static String parseDistroTimestamp(JsonNode row, String field) {
        if (!row.has(field) || row.get(field).isNull()) {
            return Instant.now().toString();
        }
        // Debezium sends PostgreSQL timestamps as microseconds since epoch
        long value = row.get(field).asLong(0);
        if (value > 1_000_000_000_000L) {
            // Microseconds since epoch
            return Instant.ofEpochMilli(value / 1000).toString();
        }
        // May be a string representation
        String text = row.get(field).asText();
        if (text != null && !text.isEmpty()) {
            return text;
        }
        return Instant.now().toString();
    }
}
