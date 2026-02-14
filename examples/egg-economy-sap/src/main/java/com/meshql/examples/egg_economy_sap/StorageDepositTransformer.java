package com.meshql.examples.egg_economy_sap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_sap.FarmTransformer.*;

/**
 * Transforms SAP ZMSEG_101 rows into clean StorageDeposit entities.
 *
 * SAP columns: MBLNR, BEHAELT_NR(FK->container), QUELL_TYP_CD, EI_MENGE, ERFAS_DT, ERFAS_ZT
 * Clean fields: container_id, source_type, source_id, eggs, timestamp, legacy_mblnr
 */
public class StorageDepositTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(StorageDepositTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> SOURCE_TYPE_MAP = Map.of(
            "F", "farm",
            "T", "transfer"
    );

    private final IdResolver idResolver;

    public StorageDepositTransformer(IdResolver idResolver) {
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

        clean.put("source_type", SOURCE_TYPE_MAP.getOrDefault(textOrNull(row, "quell_typ_cd"), "farm"));
        // source_id references the farm — use a placeholder or resolve from context
        String sourceId = idResolver.resolveFarmIdForContainer(behaeltNr);
        clean.put("source_id", sourceId != null ? sourceId : "");

        if (row.has("ei_menge") && !row.get("ei_menge").isNull()) {
            clean.put("eggs", row.get("ei_menge").asInt());
        }

        String timestamp = parseTimestamp(textOrNull(row, "erfas_dt"), textOrNull(row, "erfas_zt"));
        if (timestamp != null) {
            clean.put("timestamp", timestamp);
        }

        logger.debug("Transformed storage deposit: MBLNR={}, eggs={}", clean.get("legacy_mblnr"), clean.get("eggs"));
        return clean;
    }
}
