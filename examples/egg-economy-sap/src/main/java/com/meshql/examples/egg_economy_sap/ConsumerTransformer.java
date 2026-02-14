package com.meshql.examples.egg_economy_sap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_sap.FarmTransformer.*;

/**
 * Transforms SAP ZKUNDE_VBR rows into clean Consumer entities.
 *
 * SAP columns: KUNNR, KUND_NM, VBR_TYP_CD, ZONE_CD, WOCH_BEDARF
 * Clean fields: name, consumer_type, zone, weekly_demand, legacy_kunnr
 */
public class ConsumerTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> TYPE_MAP = Map.of(
            "H", "household",
            "R", "restaurant",
            "B", "bakery"
    );

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_kunnr", textOrNull(row, "kunnr"));
        clean.put("name", textOrNull(row, "kund_nm"));
        clean.put("consumer_type", TYPE_MAP.getOrDefault(textOrNull(row, "vbr_typ_cd"), "household"));
        clean.put("zone", textOrNull(row, "zone_cd"));

        if (row.has("woch_bedarf") && !row.get("woch_bedarf").isNull()) {
            clean.put("weekly_demand", row.get("woch_bedarf").asInt());
        }

        logger.debug("Transformed consumer: {} (KUNNR={})", clean.get("name"), clean.get("legacy_kunnr"));
        return clean;
    }
}
