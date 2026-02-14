package com.meshql.examples.egg_economy_sap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_sap.FarmTransformer.*;

/**
 * Transforms SAP ZMSEG_201 rows into clean StorageWithdrawal entities.
 *
 * SAP columns: MBLNR, BEHAELT_NR(FK->container), EI_MENGE, ERFAS_DT, ERFAS_ZT, GRUND_CD
 * Clean fields: container_id, reason, eggs, timestamp, legacy_mblnr
 */
public class StorageWithdrawalTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(StorageWithdrawalTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> REASON_MAP = Map.of(
            "S", "spoilage",
            "B", "breakage",
            "Q", "quality_check"
    );

    private final IdResolver idResolver;

    public StorageWithdrawalTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_mblnr", textOrNull(row, "mblnr"));

        // Resolve container FK
        String behaeltNr = textOrNull(row, "behaelt_nr");
        String containerId = idResolver.resolveContainerId(behaeltNr);
        if (containerId != null) {
            clean.put("container_id", containerId);
        }

        clean.put("reason", REASON_MAP.getOrDefault(textOrNull(row, "grund_cd"), "spoilage"));

        if (row.has("ei_menge") && !row.get("ei_menge").isNull()) {
            clean.put("eggs", row.get("ei_menge").asInt());
        }

        String timestamp = parseTimestamp(textOrNull(row, "erfas_dt"), textOrNull(row, "erfas_zt"));
        if (timestamp != null) {
            clean.put("timestamp", timestamp);
        }

        logger.debug("Transformed storage withdrawal: MBLNR={}, eggs={}", clean.get("legacy_mblnr"), clean.get("eggs"));
        return clean;
    }
}
