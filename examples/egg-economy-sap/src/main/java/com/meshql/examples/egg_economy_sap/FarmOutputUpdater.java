package com.meshql.examples.egg_economy_sap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FarmOutputUpdater extends ProjectionUpdater {
    private static final Logger logger = LoggerFactory.getLogger(FarmOutputUpdater.class);

    private static final String GRAPH_PATH = "/farm_output/graph";
    private static final String API_PATH = "/farm_output/api";
    private static final String FIELDS = "id farm_id farm_type eggs_today eggs_week eggs_month active_hens total_hens avg_per_hen_per_week";

    public FarmOutputUpdater(String platformUrl, ProjectionCache cache) {
        super(platformUrl, cache);
    }

    public void onLayReport(JsonNode event) {
        String farmId = event.path("farm_id").asText(null);
        int eggs = event.path("eggs").asInt(0);

        if (farmId == null) return;

        String meshqlId = cache.getFarmOutputId(farmId);

        if (meshqlId == null) {
            String farmType = resolveFarmType(farmId);

            ObjectNode data = mapper.createObjectNode();
            data.put("farm_id", farmId);
            data.put("farm_type", farmType);
            data.put("eggs_today", eggs);
            data.put("eggs_week", eggs);
            data.put("eggs_month", eggs);
            data.put("active_hens", 1);
            data.put("total_hens", 1);
            data.put("avg_per_hen_per_week", (double) eggs);

            String newId = createProjection(API_PATH, data, GRAPH_PATH, "getByFarm", farmId);
            if (newId != null) {
                cache.registerFarmOutput(farmId, newId);
                logger.info("Created farm output projection for farm {}", farmId);
            }
            return;
        }

        JsonNode current = getProjection(GRAPH_PATH, meshqlId, FIELDS);
        if (current == null || current.isNull() || current.isMissingNode()) return;

        int eggsToday = current.path("eggs_today").asInt(0) + eggs;
        int eggsWeek = current.path("eggs_week").asInt(0) + eggs;
        int eggsMonth = current.path("eggs_month").asInt(0) + eggs;
        int totalHens = current.path("total_hens").asInt(1);
        double avgPerHen = totalHens > 0 ? (double) eggsWeek / totalHens : 0;

        ObjectNode updated = mapper.createObjectNode();
        updated.put("farm_id", farmId);
        String farmType = current.path("farm_type").asText("");
        if (farmType.isEmpty()) farmType = resolveFarmType(farmId);
        updated.put("farm_type", farmType);
        updated.put("eggs_today", eggsToday);
        updated.put("eggs_week", eggsWeek);
        updated.put("eggs_month", eggsMonth);
        updated.put("active_hens", current.path("active_hens").asInt(0));
        updated.put("total_hens", totalHens);
        updated.put("avg_per_hen_per_week", avgPerHen);

        updateProjection(API_PATH, meshqlId, updated);
        logger.debug("Updated farm output for farm {}: +{} eggs, week_total={}", farmId, eggs, eggsWeek);
    }

    private String resolveFarmType(String farmId) {
        JsonNode farm = getProjection("/farm/graph", farmId, "id farm_type");
        if (farm != null && !farm.isNull() && !farm.isMissingNode()) {
            String ft = farm.path("farm_type").asText(null);
            if (ft != null && !ft.isEmpty()) return ft;
        }
        return "local_farm";
    }
}
