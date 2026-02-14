package com.meshql.examples.egg_economy_sap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_sap.FarmTransformer.*;

/**
 * Transforms SAP ZMSEG_261 rows into clean ConsumptionReport entities.
 *
 * SAP columns: MBLNR, KUNNR(FK->consumer), BEHAELT_NR(FK->container), EI_MENGE, ERFAS_DT, ERFAS_ZT, ZWECK_CD
 * Clean fields: consumer_id, container_id, eggs, timestamp, purpose, legacy_mblnr
 */
public class ConsumptionReportTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(ConsumptionReportTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> PURPOSE_MAP = Map.of(
            "K", "cooking",
            "B", "baking",
            "W", "resale",
            "R", "raw"
    );

    private final IdResolver idResolver;

    public ConsumptionReportTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_mblnr", textOrNull(row, "mblnr"));

        // Resolve consumer FK
        String kunnr = textOrNull(row, "kunnr");
        String consumerId = idResolver.resolveConsumerId(kunnr);
        if (consumerId != null) {
            clean.put("consumer_id", consumerId);
        }

        // Resolve container FK
        String behaeltNr = textOrNull(row, "behaelt_nr");
        String containerId = idResolver.resolveContainerId(behaeltNr);
        if (containerId != null) {
            clean.put("container_id", containerId);
        }

        if (row.has("ei_menge") && !row.get("ei_menge").isNull()) {
            clean.put("eggs", row.get("ei_menge").asInt());
        }

        String timestamp = parseTimestamp(textOrNull(row, "erfas_dt"), textOrNull(row, "erfas_zt"));
        if (timestamp != null) {
            clean.put("timestamp", timestamp);
        }

        clean.put("purpose", PURPOSE_MAP.getOrDefault(textOrNull(row, "zweck_cd"), "cooking"));

        logger.debug("Transformed consumption report: MBLNR={}, eggs={}", clean.get("legacy_mblnr"), clean.get("eggs"));
        return clean;
    }
}
