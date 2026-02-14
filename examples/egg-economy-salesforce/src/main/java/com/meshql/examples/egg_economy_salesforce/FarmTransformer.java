package com.meshql.examples.egg_economy_salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Transforms Salesforce Farm__c rows into clean Farm entities.
 *
 * SF columns: Id(18-char), Name(auto), Farm_Type__c(picklist), Zone__c, Owner_Name__c, IsDeleted
 * Clean fields: name, farm_type, zone, owner, legacy_sf_id
 */
public class FarmTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(FarmTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> FARM_TYPE_MAP = Map.of(
            "Megafarm", "megafarm",
            "Local Farm", "local_farm",
            "Homestead", "homestead"
    );

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_sf_id", textOrNull(row, "id"));
        clean.put("name", textOrNull(row, "name"));
        String farmType = textOrNull(row, "farm_type__c");
        clean.put("farm_type", FARM_TYPE_MAP.getOrDefault(farmType, farmType != null ? farmType.toLowerCase().replace(" ", "_") : "local_farm"));
        clean.put("zone", textOrNull(row, "zone__c"));
        putIfPresent(clean, "owner", textOrNull(row, "owner_name__c"));

        logger.debug("Transformed farm: {} (SF Id={})", clean.get("name"), clean.get("legacy_sf_id"));
        return clean;
    }

    static String textOrNull(JsonNode row, String field) {
        if (row == null || !row.has(field) || row.get(field).isNull()) return null;
        return row.get(field).asText();
    }

    static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isEmpty()) {
            node.put(field, value);
        }
    }
}
