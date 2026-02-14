package com.meshql.examples.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.enterprise.FarmTransformer.*;

/**
 * Transforms SAP ZSTALL_MSTR rows into clean Coop entities.
 *
 * SAP columns: STALL_NR, WERKS(FK->farm), STALL_NM, KAPZT, STALL_TYP_CD
 * Clean fields: name, farm_id, capacity, coop_type, legacy_stall_nr
 */
public class CoopTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(CoopTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> TYPE_MAP = Map.of(
            "F", "free_range",
            "C", "cage",
            "B", "barn"
    );

    private final IdResolver idResolver;

    public CoopTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_stall_nr", textOrNull(row, "stall_nr"));
        clean.put("name", textOrNull(row, "stall_nm"));

        String werks = textOrNull(row, "werks");
        String farmId = idResolver.resolveFarmId(werks);
        if (farmId != null) {
            clean.put("farm_id", farmId);
        }

        if (row.has("kapzt") && !row.get("kapzt").isNull()) {
            clean.put("capacity", row.get("kapzt").asInt());
        }
        clean.put("coop_type", TYPE_MAP.getOrDefault(textOrNull(row, "stall_typ_cd"), "barn"));

        logger.debug("Transformed coop: {} (STALL_NR={}, WERKS={}->{})",
                clean.get("name"), clean.get("legacy_stall_nr"), werks, farmId);
        return clean;
    }
}
