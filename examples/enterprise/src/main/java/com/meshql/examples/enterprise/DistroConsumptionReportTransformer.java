package com.meshql.examples.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.meshql.examples.enterprise.DistroStorageDepositTransformer.parseDistroTimestamp;

/**
 * Transforms distribution system `consumption_reports` rows into clean ConsumptionReport entities.
 *
 * Distro columns: id, customer_id(FK), container_id(FK), eggs, purpose, recorded_at
 * Clean fields: consumer_id, container_id, eggs, timestamp, purpose, legacy_distro_id
 */
public class DistroConsumptionReportTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(DistroConsumptionReportTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final IdResolver idResolver;

    public DistroConsumptionReportTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_distro_id", String.valueOf(row.path("id").asInt()));

        // Resolve consumer FK
        String distroCustomerId = String.valueOf(row.path("customer_id").asInt());
        String consumerId = idResolver.resolveDistroConsumerId(distroCustomerId);
        if (consumerId != null) {
            clean.put("consumer_id", consumerId);
        }

        // Resolve container FK
        String distroContainerId = String.valueOf(row.path("container_id").asInt());
        String containerId = idResolver.resolveDistroContainerId(distroContainerId);
        if (containerId != null) {
            clean.put("container_id", containerId);
        }

        clean.put("eggs", row.path("eggs").asInt(0));
        clean.put("timestamp", parseDistroTimestamp(row, "recorded_at"));
        clean.put("purpose", row.path("purpose").asText("cooking"));

        logger.debug("Transformed distro consumption report: id={}, eggs={}", clean.get("legacy_distro_id"), clean.get("eggs"));
        return clean;
    }
}
