package com.meshql.examples.egg_economy_salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_salesforce.FarmTransformer.*;

/**
 * Transforms Salesforce Hen__c rows into clean Hen entities.
 *
 * SF columns: Id, Name, Coop__c(lookup), Breed__c(picklist), Date_of_Birth__c, Status__c(picklist)
 * Clean fields: name, coop_id, breed, dob, status, legacy_sf_id
 */
public class HenTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(HenTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> BREED_MAP = Map.of(
            "Rhode Island Red", "rhode_island_red",
            "Leghorn", "leghorn",
            "Plymouth Rock", "plymouth_rock",
            "Sussex", "sussex",
            "Orpington", "orpington"
    );

    private static final Map<String, String> STATUS_MAP = Map.of(
            "Active", "active",
            "Retired", "retired",
            "Deceased", "deceased"
    );

    private final IdResolver idResolver;

    public HenTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_sf_id", textOrNull(row, "id"));
        clean.put("name", textOrNull(row, "name"));

        // Resolve coop FK: Coop__c (SF lookup ID) -> coop_id
        String coopSfId = textOrNull(row, "coop__c");
        String coopId = idResolver.resolveCoopId(coopSfId);
        if (coopId != null) {
            clean.put("coop_id", coopId);
        }

        String breed = textOrNull(row, "breed__c");
        clean.put("breed", BREED_MAP.getOrDefault(breed, breed != null ? breed.toLowerCase().replace(" ", "_") : "leghorn"));

        // SF dates are already ISO format (YYYY-MM-DD)
        putIfPresent(clean, "dob", textOrNull(row, "date_of_birth__c"));

        String status = textOrNull(row, "status__c");
        clean.put("status", STATUS_MAP.getOrDefault(status, status != null ? status.toLowerCase() : "active"));

        logger.debug("Transformed hen: {} (SF Id={}, Coop__c={}->{})",
                clean.get("name"), clean.get("legacy_sf_id"), coopSfId, coopId);
        return clean;
    }
}
