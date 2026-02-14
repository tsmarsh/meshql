package com.meshql.examples.egg_economy_salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_salesforce.FarmTransformer.*;

/**
 * Transforms Salesforce Storage_Withdrawal__c rows into clean StorageWithdrawal entities.
 *
 * SF columns: Id, Container__c(FK), Egg_Count__c, Reason__c(picklist), CreatedDate
 * Clean fields: container_id, reason, eggs, timestamp, legacy_sf_id
 */
public class StorageWithdrawalTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(StorageWithdrawalTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> REASON_MAP = Map.of(
            "Spoilage", "spoilage",
            "Breakage", "breakage",
            "Quality Check", "quality_check"
    );

    private final IdResolver idResolver;

    public StorageWithdrawalTransformer(IdResolver idResolver) {
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

        String reason = textOrNull(row, "reason__c");
        clean.put("reason", REASON_MAP.getOrDefault(reason, reason != null ? reason.toLowerCase().replace(" ", "_") : "spoilage"));

        if (row.has("egg_count__c") && !row.get("egg_count__c").isNull()) {
            clean.put("eggs", row.get("egg_count__c").asInt());
        }

        putIfPresent(clean, "timestamp", textOrNull(row, "createddate"));

        logger.debug("Transformed storage withdrawal: SF Id={}, eggs={}", clean.get("legacy_sf_id"), clean.get("eggs"));
        return clean;
    }
}
