package com.meshql.examples.egg_economy_sap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_sap.FarmTransformer.*;

/**
 * Transforms SAP ZMSEG_301 rows into clean ContainerTransfer entities.
 *
 * SAP columns: MBLNR, QUELL_BEH_NR(FK->source container), ZIEL_BEH_NR(FK->dest container), EI_MENGE, ERFAS_DT, ERFAS_ZT, TRANS_CD
 * Clean fields: source_container_id, dest_container_id, eggs, timestamp, transport_method, legacy_mblnr
 */
public class ContainerTransferTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(ContainerTransferTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> TRANSPORT_MAP = Map.of(
            "T", "truck",
            "V", "van",
            "K", "cart"
    );

    private final IdResolver idResolver;

    public ContainerTransferTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_mblnr", textOrNull(row, "mblnr"));

        // Resolve source container FK
        String quellBehNr = textOrNull(row, "quell_beh_nr");
        String sourceContainerId = idResolver.resolveContainerId(quellBehNr);
        if (sourceContainerId != null) {
            clean.put("source_container_id", sourceContainerId);
        }

        // Resolve dest container FK
        String zielBehNr = textOrNull(row, "ziel_beh_nr");
        String destContainerId = idResolver.resolveContainerId(zielBehNr);
        if (destContainerId != null) {
            clean.put("dest_container_id", destContainerId);
        }

        if (row.has("ei_menge") && !row.get("ei_menge").isNull()) {
            clean.put("eggs", row.get("ei_menge").asInt());
        }

        String timestamp = parseTimestamp(textOrNull(row, "erfas_dt"), textOrNull(row, "erfas_zt"));
        if (timestamp != null) {
            clean.put("timestamp", timestamp);
        }

        clean.put("transport_method", TRANSPORT_MAP.getOrDefault(textOrNull(row, "trans_cd"), "truck"));

        logger.debug("Transformed container transfer: MBLNR={}, eggs={}", clean.get("legacy_mblnr"), clean.get("eggs"));
        return clean;
    }
}
