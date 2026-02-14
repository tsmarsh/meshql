package com.meshql.examples.egg_economy_salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_salesforce.FarmTransformer.*;

/**
 * Transforms Salesforce Lay_Report__c rows into clean LayReport entities.
 *
 * SF columns: Id, Hen__c(FK), Farm__c(FK), Egg_Count__c, Quality__c(picklist), CreatedDate
 * Clean fields: hen_id, coop_id, farm_id, eggs, timestamp, quality, legacy_sf_id
 */
public class LayReportTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(LayReportTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> QUALITY_MAP = Map.of(
            "Grade A", "grade_a",
            "Grade B", "grade_b",
            "Cracked", "cracked",
            "Double Yolk", "double_yolk"
    );

    private final IdResolver idResolver;

    public LayReportTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_sf_id", textOrNull(row, "id"));

        // Resolve hen FK
        String henSfId = textOrNull(row, "hen__c");
        String henId = idResolver.resolveHenId(henSfId);
        if (henId != null) {
            clean.put("hen_id", henId);
        }

        // Resolve coop from hen
        String coopId = idResolver.resolveCoopIdForHen(henSfId);
        if (coopId != null) {
            clean.put("coop_id", coopId);
        }

        // Resolve farm FK
        String farmSfId = textOrNull(row, "farm__c");
        String farmId = idResolver.resolveFarmId(farmSfId);
        if (farmId != null) {
            clean.put("farm_id", farmId);
        }

        if (row.has("egg_count__c") && !row.get("egg_count__c").isNull()) {
            clean.put("eggs", row.get("egg_count__c").asInt());
        }

        // SF CreatedDate is already ISO 8601 (e.g., 2026-02-01T06:30:00.000Z)
        putIfPresent(clean, "timestamp", textOrNull(row, "createddate"));

        String quality = textOrNull(row, "quality__c");
        clean.put("quality", QUALITY_MAP.getOrDefault(quality, quality != null ? quality.toLowerCase().replace(" ", "_") : "grade_a"));

        logger.debug("Transformed lay report: SF Id={}, eggs={}", clean.get("legacy_sf_id"), clean.get("eggs"));
        return clean;
    }
}
