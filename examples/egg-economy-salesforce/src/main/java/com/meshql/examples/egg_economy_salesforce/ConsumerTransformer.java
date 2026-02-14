package com.meshql.examples.egg_economy_salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_salesforce.FarmTransformer.*;

/**
 * Transforms Salesforce Consumer_Account__c rows into clean Consumer entities.
 *
 * SF columns: Id, Name, Consumer_Type__c(picklist), Zone__c, Weekly_Demand__c
 * Clean fields: name, consumer_type, zone, weekly_demand, legacy_sf_id
 */
public class ConsumerTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> TYPE_MAP = Map.of(
            "Household", "household",
            "Restaurant", "restaurant",
            "Bakery", "bakery"
    );

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_sf_id", textOrNull(row, "id"));
        clean.put("name", textOrNull(row, "name"));
        String consumerType = textOrNull(row, "consumer_type__c");
        clean.put("consumer_type", TYPE_MAP.getOrDefault(consumerType, consumerType != null ? consumerType.toLowerCase() : "household"));
        clean.put("zone", textOrNull(row, "zone__c"));

        if (row.has("weekly_demand__c") && !row.get("weekly_demand__c").isNull()) {
            clean.put("weekly_demand", row.get("weekly_demand__c").asInt());
        }

        logger.debug("Transformed consumer: {} (SF Id={})", clean.get("name"), clean.get("legacy_sf_id"));
        return clean;
    }
}
