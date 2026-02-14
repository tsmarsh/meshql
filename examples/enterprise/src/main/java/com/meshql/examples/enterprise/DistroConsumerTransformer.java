package com.meshql.examples.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transforms distribution system `customers` rows into clean Consumer entities.
 *
 * Distro columns: id(SERIAL), name, customer_type, zone, weekly_demand, email
 * Clean fields: name, consumer_type, zone, weekly_demand, legacy_distro_id
 */
public class DistroConsumerTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(DistroConsumerTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_distro_id", String.valueOf(row.path("id").asInt()));
        clean.put("name", row.path("name").asText(""));
        clean.put("consumer_type", row.path("customer_type").asText("household"));
        clean.put("zone", row.path("zone").asText(""));

        if (row.has("weekly_demand") && !row.get("weekly_demand").isNull()) {
            clean.put("weekly_demand", row.get("weekly_demand").asInt());
        }

        logger.debug("Transformed distro consumer: {} (id={})", clean.get("name"), clean.get("legacy_distro_id"));
        return clean;
    }
}
