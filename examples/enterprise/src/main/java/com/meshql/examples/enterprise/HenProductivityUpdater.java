package com.meshql.examples.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HenProductivityUpdater extends ProjectionUpdater {
    private static final Logger logger = LoggerFactory.getLogger(HenProductivityUpdater.class);

    private static final String GRAPH_PATH = "/hen_productivity/graph";
    private static final String API_PATH = "/hen_productivity/api";
    private static final String FIELDS = "id hen_id farm_id eggs_today eggs_week eggs_month avg_per_week total_eggs quality_rate";

    public HenProductivityUpdater(String platformUrl, ProjectionCache cache) {
        super(platformUrl, cache);
    }

    public void onLayReport(JsonNode event) {
        String henId = event.path("hen_id").asText(null);
        String farmId = event.path("farm_id").asText(null);
        int eggs = event.path("eggs").asInt(0);
        String quality = event.path("quality").asText("grade_a");
        boolean isGradeA = "grade_a".equals(quality) || "double_yolk".equals(quality);

        if (henId == null) return;

        String meshqlId = cache.getHenProductivityId(henId);

        if (meshqlId == null) {
            ObjectNode data = mapper.createObjectNode();
            data.put("hen_id", henId);
            data.put("farm_id", farmId != null ? farmId : "");
            data.put("eggs_today", eggs);
            data.put("eggs_week", eggs);
            data.put("eggs_month", eggs);
            data.put("avg_per_week", (double) eggs);
            data.put("total_eggs", eggs);
            data.put("quality_rate", isGradeA ? 1.0 : 0.0);

            String newId = createProjection(API_PATH, data, GRAPH_PATH, "getByHen", henId);
            if (newId != null) {
                cache.registerHenProductivity(henId, newId);
                logger.info("Created hen productivity projection for hen {}", henId);
            }
            return;
        }

        JsonNode current = getProjection(GRAPH_PATH, meshqlId, FIELDS);
        if (current == null || current.isNull() || current.isMissingNode()) return;

        int totalEggs = current.path("total_eggs").asInt(0) + eggs;
        int eggsWeek = current.path("eggs_week").asInt(0) + eggs;
        int eggsMonth = current.path("eggs_month").asInt(0) + eggs;
        int eggsToday = current.path("eggs_today").asInt(0) + eggs;

        double currentRate = current.path("quality_rate").asDouble(1.0);
        int previousTotal = current.path("total_eggs").asInt(0);
        double newRate = previousTotal > 0
                ? (currentRate * previousTotal + (isGradeA ? eggs : 0)) / totalEggs
                : (isGradeA ? 1.0 : 0.0);

        ObjectNode updated = mapper.createObjectNode();
        updated.put("hen_id", henId);
        updated.put("farm_id", farmId != null ? farmId : current.path("farm_id").asText(""));
        updated.put("eggs_today", eggsToday);
        updated.put("eggs_week", eggsWeek);
        updated.put("eggs_month", eggsMonth);
        updated.put("avg_per_week", (double) eggsWeek);
        updated.put("total_eggs", totalEggs);
        updated.put("quality_rate", newRate);

        updateProjection(API_PATH, meshqlId, updated);
        logger.debug("Updated hen productivity for hen {}: +{} eggs, total={}", henId, eggs, totalEggs);
    }
}
