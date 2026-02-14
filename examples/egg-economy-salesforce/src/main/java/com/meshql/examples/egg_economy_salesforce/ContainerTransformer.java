package com.meshql.examples.egg_economy_salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_salesforce.FarmTransformer.*;

/**
 * Transforms Salesforce Storage_Container__c rows into clean Container entities.
 *
 * SF columns: Id, Name, Container_Type__c(picklist), Capacity__c, Zone__c
 * Clean fields: name, container_type, capacity, zone, legacy_sf_id
 */
public class ContainerTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(ContainerTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> TYPE_MAP = Map.of(
            "Warehouse", "warehouse",
            "Market Shelf", "market_shelf",
            "Fridge", "fridge"
    );

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_sf_id", textOrNull(row, "id"));
        clean.put("name", textOrNull(row, "name"));
        String containerType = textOrNull(row, "container_type__c");
        clean.put("container_type", TYPE_MAP.getOrDefault(containerType, containerType != null ? containerType.toLowerCase().replace(" ", "_") : "warehouse"));

        if (row.has("capacity__c") && !row.get("capacity__c").isNull()) {
            clean.put("capacity", row.get("capacity__c").asInt());
        }
        clean.put("zone", textOrNull(row, "zone__c"));

        logger.debug("Transformed container: {} (SF Id={})", clean.get("name"), clean.get("legacy_sf_id"));
        return clean;
    }
}
