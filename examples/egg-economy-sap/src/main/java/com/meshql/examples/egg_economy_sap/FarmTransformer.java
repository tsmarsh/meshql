package com.meshql.examples.egg_economy_sap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Transforms SAP ZFARM_MSTR rows into clean Farm entities.
 *
 * SAP columns: MANDT, WERKS, FARM_NM, FARM_TYP_CD, ZONE_CD, EIGR, ERDAT
 * Clean fields: name, farm_type, zone, owner, legacy_werks
 */
public class FarmTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(FarmTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> FARM_TYPE_MAP = Map.of(
            "M", "megafarm",
            "L", "local_farm",
            "H", "homestead"
    );

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_werks", textOrNull(row, "werks"));
        clean.put("name", textOrNull(row, "farm_nm"));
        clean.put("farm_type", FARM_TYPE_MAP.getOrDefault(textOrNull(row, "farm_typ_cd"), "local_farm"));
        clean.put("zone", textOrNull(row, "zone_cd"));
        putIfPresent(clean, "owner", textOrNull(row, "eigr"));

        logger.debug("Transformed farm: {} (WERKS={})", clean.get("name"), clean.get("legacy_werks"));
        return clean;
    }

    static String textOrNull(JsonNode row, String field) {
        if (row == null || !row.has(field) || row.get(field).isNull()) return null;
        return row.get(field).asText();
    }

    static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isEmpty()) {
            node.put(field, value);
        }
    }

    static String parseDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.length() != 8) return null;
        return yyyymmdd.substring(0, 4) + "-" + yyyymmdd.substring(4, 6) + "-" + yyyymmdd.substring(6, 8);
    }

    static String parseTimestamp(String date, String time) {
        String d = parseDate(date);
        if (d == null) return null;
        if (time != null && time.length() == 6) {
            return d + "T" + time.substring(0, 2) + ":" + time.substring(2, 4) + ":" + time.substring(4, 6) + "Z";
        }
        return d + "T00:00:00Z";
    }
}
