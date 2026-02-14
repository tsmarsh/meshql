package com.meshql.examples.egg_economy_sap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_sap.FarmTransformer.*;

/**
 * Transforms SAP ZBEHAELT_MSTR rows into clean Container entities.
 *
 * SAP columns: BEHAELT_NR, BEHAELT_NM, BEHAELT_TYP_CD, KAPZT, ZONE_CD
 * Clean fields: name, container_type, capacity, zone, legacy_behaelt_nr
 */
public class ContainerTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(ContainerTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> TYPE_MAP = Map.of(
            "W", "warehouse",
            "S", "market_shelf",
            "F", "fridge"
    );

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_behaelt_nr", textOrNull(row, "behaelt_nr"));
        clean.put("name", textOrNull(row, "behaelt_nm"));
        clean.put("container_type", TYPE_MAP.getOrDefault(textOrNull(row, "behaelt_typ_cd"), "warehouse"));

        if (row.has("kapzt") && !row.get("kapzt").isNull()) {
            clean.put("capacity", row.get("kapzt").asInt());
        }
        clean.put("zone", textOrNull(row, "zone_cd"));

        logger.debug("Transformed container: {} (BEHAELT_NR={})", clean.get("name"), clean.get("legacy_behaelt_nr"));
        return clean;
    }
}
