package com.meshql.examples.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.meshql.examples.enterprise.DistroStorageDepositTransformer.parseDistroTimestamp;

/**
 * Transforms distribution system `storage_withdrawals` rows into clean StorageWithdrawal entities.
 *
 * Distro columns: id, container_id(FK), reason, eggs, recorded_at
 * Clean fields: container_id, reason, eggs, timestamp, legacy_distro_id
 */
public class DistroStorageWithdrawalTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(DistroStorageWithdrawalTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final IdResolver idResolver;

    public DistroStorageWithdrawalTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_distro_id", String.valueOf(row.path("id").asInt()));

        String distroContainerId = String.valueOf(row.path("container_id").asInt());
        String containerId = idResolver.resolveDistroContainerId(distroContainerId);
        if (containerId != null) {
            clean.put("container_id", containerId);
        }

        clean.put("reason", row.path("reason").asText("spoilage"));
        clean.put("eggs", row.path("eggs").asInt(0));
        clean.put("timestamp", parseDistroTimestamp(row, "recorded_at"));

        logger.debug("Transformed distro storage withdrawal: id={}, eggs={}", clean.get("legacy_distro_id"), clean.get("eggs"));
        return clean;
    }
}
