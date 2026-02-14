package com.meshql.examples.egg_economy_salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_salesforce.FarmTransformer.*;

/**
 * Transforms Salesforce Consumption_Report__c rows into clean ConsumptionReport entities.
 *
 * SF columns: Id, Consumer__c(FK), Container__c(FK), Egg_Count__c, Purpose__c(picklist), CreatedDate
 * Clean fields: consumer_id, container_id, eggs, timestamp, purpose, legacy_sf_id
 */
public class ConsumptionReportTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(ConsumptionReportTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> PURPOSE_MAP = Map.of(
            "Cooking", "cooking",
            "Baking", "baking",
            "Resale", "resale",
            "Raw", "raw"
    );

    private final IdResolver idResolver;

    public ConsumptionReportTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_sf_id", textOrNull(row, "id"));

        // Resolve consumer FK
        String consumerSfId = textOrNull(row, "consumer__c");
        String consumerId = idResolver.resolveConsumerId(consumerSfId);
        if (consumerId != null) {
            clean.put("consumer_id", consumerId);
        }

        // Resolve container FK
        String containerSfId = textOrNull(row, "container__c");
        String containerId = idResolver.resolveContainerId(containerSfId);
        if (containerId != null) {
            clean.put("container_id", containerId);
        }

        if (row.has("egg_count__c") && !row.get("egg_count__c").isNull()) {
            clean.put("eggs", row.get("egg_count__c").asInt());
        }

        putIfPresent(clean, "timestamp", textOrNull(row, "createddate"));

        String purpose = textOrNull(row, "purpose__c");
        clean.put("purpose", PURPOSE_MAP.getOrDefault(purpose, purpose != null ? purpose.toLowerCase() : "cooking"));

        logger.debug("Transformed consumption report: SF Id={}, eggs={}", clean.get("legacy_sf_id"), clean.get("eggs"));
        return clean;
    }
}
