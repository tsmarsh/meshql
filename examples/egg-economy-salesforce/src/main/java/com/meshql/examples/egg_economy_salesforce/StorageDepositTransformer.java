package com.meshql.examples.egg_economy_salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_salesforce.FarmTransformer.*;

/**
 * Transforms Salesforce Storage_Deposit__c rows into clean StorageDeposit entities.
 *
 * SF columns: Id, Container__c(FK), Source_Type__c(picklist), Egg_Count__c, CreatedDate
 * Clean fields: container_id, source_type, source_id, eggs, timestamp, legacy_sf_id
 */
public class StorageDepositTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(StorageDepositTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> SOURCE_TYPE_MAP = Map.of(
            "Farm", "farm",
            "Transfer", "transfer"
    );

    private final IdResolver idResolver;

    public StorageDepositTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_sf_id", textOrNull(row, "id"));

        // Resolve container FK
        String containerSfId = textOrNull(row, "container__c");
        String containerId = idResolver.resolveContainerId(containerSfId);
        if (containerId != null) {
            clean.put("container_id", containerId);
        }

        String sourceType = textOrNull(row, "source_type__c");
        clean.put("source_type", SOURCE_TYPE_MAP.getOrDefault(sourceType, sourceType != null ? sourceType.toLowerCase() : "farm"));
        String sourceId = idResolver.resolveFarmIdForContainer(containerSfId);
        clean.put("source_id", sourceId != null ? sourceId : "");

        if (row.has("egg_count__c") && !row.get("egg_count__c").isNull()) {
            clean.put("eggs", row.get("egg_count__c").asInt());
        }

        putIfPresent(clean, "timestamp", textOrNull(row, "createddate"));

        logger.debug("Transformed storage deposit: SF Id={}, eggs={}", clean.get("legacy_sf_id"), clean.get("eggs"));
        return clean;
    }
}
