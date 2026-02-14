package com.meshql.examples.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.meshql.examples.enterprise.DistroStorageDepositTransformer.parseDistroTimestamp;

/**
 * Transforms distribution system `container_transfers` rows into clean ContainerTransfer entities.
 *
 * Distro columns: id, source_container_id(FK), dest_container_id(FK), eggs, transport_method, recorded_at
 * Clean fields: source_container_id, dest_container_id, eggs, timestamp, transport_method, legacy_distro_id
 */
public class DistroContainerTransferTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(DistroContainerTransferTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final IdResolver idResolver;

    public DistroContainerTransferTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_distro_id", String.valueOf(row.path("id").asInt()));

        // Resolve source container FK
        String distroSourceId = String.valueOf(row.path("source_container_id").asInt());
        String sourceContainerId = idResolver.resolveDistroContainerId(distroSourceId);
        if (sourceContainerId != null) {
            clean.put("source_container_id", sourceContainerId);
        }

        // Resolve dest container FK
        String distroDestId = String.valueOf(row.path("dest_container_id").asInt());
        String destContainerId = idResolver.resolveDistroContainerId(distroDestId);
        if (destContainerId != null) {
            clean.put("dest_container_id", destContainerId);
        }

        clean.put("eggs", row.path("eggs").asInt(0));
        clean.put("timestamp", parseDistroTimestamp(row, "recorded_at"));
        clean.put("transport_method", row.path("transport_method").asText("truck"));

        logger.debug("Transformed distro container transfer: id={}, eggs={}", clean.get("legacy_distro_id"), clean.get("eggs"));
        return clean;
    }
}
