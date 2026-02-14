package com.meshql.examples.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transforms distribution system `containers` rows into clean Container entities.
 *
 * Distro columns: id(SERIAL), name, container_type, capacity, zone, location_code
 * Clean fields: name, container_type, capacity, zone, legacy_distro_id
 */
public class DistroContainerTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(DistroContainerTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_distro_id", String.valueOf(row.path("id").asInt()));
        clean.put("name", row.path("name").asText(""));
        clean.put("container_type", row.path("container_type").asText("warehouse"));
        clean.put("capacity", row.path("capacity").asInt(0));
        clean.put("zone", row.path("zone").asText(""));

        logger.debug("Transformed distro container: {} (id={})", clean.get("name"), clean.get("legacy_distro_id"));
        return clean;
    }
}
