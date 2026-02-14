package com.meshql.examples.egg_economy_salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_salesforce.FarmTransformer.*;

/**
 * Transforms Salesforce Coop__c rows into clean Coop entities.
 *
 * SF columns: Id, Name, Farm__c(lookup), Capacity__c, Coop_Type__c(picklist)
 * Clean fields: name, farm_id, capacity, coop_type, legacy_sf_id
 */
public class CoopTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(CoopTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> TYPE_MAP = Map.of(
            "Free Range", "free_range",
            "Cage", "cage",
            "Barn", "barn"
    );

    private final IdResolver idResolver;

    public CoopTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_sf_id", textOrNull(row, "id"));
        clean.put("name", textOrNull(row, "name"));

        // Resolve farm FK: Farm__c (SF lookup ID) -> farm_id
        String farmSfId = textOrNull(row, "farm__c");
        String farmId = idResolver.resolveFarmId(farmSfId);
        if (farmId != null) {
            clean.put("farm_id", farmId);
        }

        if (row.has("capacity__c") && !row.get("capacity__c").isNull()) {
            clean.put("capacity", row.get("capacity__c").asInt());
        }
        String coopType = textOrNull(row, "coop_type__c");
        clean.put("coop_type", TYPE_MAP.getOrDefault(coopType, coopType != null ? coopType.toLowerCase().replace(" ", "_") : "barn"));

        logger.debug("Transformed coop: {} (SF Id={}, Farm__c={}->{})",
                clean.get("name"), clean.get("legacy_sf_id"), farmSfId, farmId);
        return clean;
    }
}
