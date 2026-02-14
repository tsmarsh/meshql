package com.meshql.examples.egg_economy_sap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_sap.FarmTransformer.*;

/**
 * Transforms SAP ZEQUI_HEN rows into clean Hen entities.
 *
 * SAP columns: EQUNR, STALL_NR(FK->coop), HENNE_NM, RASSE_CD, GEB_DT, STAT_CD
 * Clean fields: name, coop_id, breed, dob, status, legacy_equnr
 */
public class HenTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(HenTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> BREED_MAP = Map.of(
            "RIR", "rhode_island_red",
            "LEG", "leghorn",
            "PLY", "plymouth_rock",
            "SUS", "sussex",
            "ORG", "orpington"
    );

    private static final Map<String, String> STATUS_MAP = Map.of(
            "A", "active",
            "R", "retired",
            "D", "deceased"
    );

    private final IdResolver idResolver;

    public HenTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_equnr", textOrNull(row, "equnr"));
        clean.put("name", textOrNull(row, "henne_nm"));

        // Resolve coop FK: STALL_NR -> coop_id
        String stallNr = textOrNull(row, "stall_nr");
        String coopId = idResolver.resolveCoopId(stallNr);
        if (coopId != null) {
            clean.put("coop_id", coopId);
        }

        clean.put("breed", BREED_MAP.getOrDefault(textOrNull(row, "rasse_cd"), "leghorn"));
        putIfPresent(clean, "dob", parseDate(textOrNull(row, "geb_dt")));
        clean.put("status", STATUS_MAP.getOrDefault(textOrNull(row, "stat_cd"), "active"));

        logger.debug("Transformed hen: {} (EQUNR={}, STALL_NR={}->{})",
                clean.get("name"), clean.get("legacy_equnr"), stallNr, coopId);
        return clean;
    }
}
