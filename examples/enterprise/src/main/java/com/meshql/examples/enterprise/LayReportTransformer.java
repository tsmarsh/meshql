package com.meshql.examples.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.enterprise.FarmTransformer.*;

/**
 * Transforms SAP ZAFRU_LEGE rows into clean LayReport entities.
 *
 * SAP columns: AUFNR, EQUNR(FK->hen), WERKS(FK->farm), EI_MENGE, ERFAS_DT, ERFAS_ZT, QUAL_CD
 * Clean fields: hen_id, coop_id, farm_id, eggs, timestamp, quality, legacy_aufnr
 */
public class LayReportTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(LayReportTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> QUALITY_MAP = Map.of(
            "A", "grade_a",
            "B", "grade_b",
            "C", "cracked",
            "D", "double_yolk"
    );

    private final IdResolver idResolver;

    public LayReportTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_aufnr", textOrNull(row, "aufnr"));

        String equnr = textOrNull(row, "equnr");
        String henId = idResolver.resolveHenId(equnr);
        if (henId != null) {
            clean.put("hen_id", henId);
        }

        String coopId = idResolver.resolveCoopIdForHen(equnr);
        if (coopId != null) {
            clean.put("coop_id", coopId);
        }

        String werks = textOrNull(row, "werks");
        String farmId = idResolver.resolveFarmId(werks);
        if (farmId != null) {
            clean.put("farm_id", farmId);
        }

        if (row.has("ei_menge") && !row.get("ei_menge").isNull()) {
            clean.put("eggs", row.get("ei_menge").asInt());
        }

        String timestamp = parseTimestamp(textOrNull(row, "erfas_dt"), textOrNull(row, "erfas_zt"));
        if (timestamp != null) {
            clean.put("timestamp", timestamp);
        }

        clean.put("quality", QUALITY_MAP.getOrDefault(textOrNull(row, "qual_cd"), "grade_a"));

        logger.debug("Transformed lay report: AUFNR={}, eggs={}", clean.get("legacy_aufnr"), clean.get("eggs"));
        return clean;
    }
}
