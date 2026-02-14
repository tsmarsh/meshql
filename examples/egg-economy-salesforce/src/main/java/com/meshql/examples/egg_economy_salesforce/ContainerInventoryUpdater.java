package com.meshql.examples.egg_economy_salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContainerInventoryUpdater extends ProjectionUpdater {
    private static final Logger logger = LoggerFactory.getLogger(ContainerInventoryUpdater.class);

    private static final String GRAPH_PATH = "/container_inventory/graph";
    private static final String API_PATH = "/container_inventory/api";
    private static final String FIELDS = "id container_id current_eggs total_deposits total_withdrawals total_transfers_in total_transfers_out total_consumed utilization_pct";

    public ContainerInventoryUpdater(String platformUrl, ProjectionCache cache) {
        super(platformUrl, cache);
    }

    public void onStorageDeposit(JsonNode event) {
        String containerId = event.path("container_id").asText(null);
        int eggs = event.path("eggs").asInt(0);
        if (containerId == null) return;

        updateContainer(containerId, eggs, 0, 0, 0, eggs, 0);
        logger.debug("StorageDeposit: +{} eggs to container {}", eggs, containerId);
    }

    public void onStorageWithdrawal(JsonNode event) {
        String containerId = event.path("container_id").asText(null);
        int eggs = event.path("eggs").asInt(0);
        if (containerId == null) return;

        updateContainer(containerId, -eggs, 0, eggs, 0, 0, 0);
        logger.debug("StorageWithdrawal: -{} eggs from container {}", eggs, containerId);
    }

    public void onContainerTransferSource(String containerId, int eggs) {
        updateContainer(containerId, -eggs, 0, 0, 0, 0, eggs);
        logger.debug("ContainerTransfer: -{} eggs from source container {}", eggs, containerId);
    }

    public void onContainerTransferDest(String containerId, int eggs) {
        updateContainer(containerId, eggs, 0, 0, eggs, 0, 0);
        logger.debug("ContainerTransfer: +{} eggs to dest container {}", eggs, containerId);
    }

    public void onConsumption(JsonNode event) {
        String containerId = event.path("container_id").asText(null);
        int eggs = event.path("eggs").asInt(0);
        if (containerId == null) return;

        updateContainer(containerId, -eggs, 0, 0, 0, 0, 0);
        updateContainerConsumed(containerId, eggs);
        logger.debug("ConsumptionReport: -{} eggs from container {}", eggs, containerId);
    }

    private void updateContainer(String containerId, int eggsDelta, int depositsDelta,
                                  int withdrawalsDelta, int transfersInDelta,
                                  int depositTotal, int transfersOutDelta) {
        String meshqlId = cache.getContainerInventoryId(containerId);

        if (meshqlId == null) {
            ObjectNode data = mapper.createObjectNode();
            data.put("container_id", containerId);
            data.put("current_eggs", Math.max(0, eggsDelta));
            data.put("total_deposits", depositTotal);
            data.put("total_withdrawals", withdrawalsDelta);
            data.put("total_transfers_in", transfersInDelta);
            data.put("total_transfers_out", transfersOutDelta);
            data.put("total_consumed", 0);
            data.put("utilization_pct", 0.0);

            String newId = createProjection(API_PATH, data, GRAPH_PATH, "getByContainer", containerId);
            if (newId != null) {
                cache.registerContainerInventory(containerId, newId);
                logger.info("Created container inventory projection for container {}", containerId);
            }
            return;
        }

        JsonNode current = getProjection(GRAPH_PATH, meshqlId, FIELDS);
        if (current == null || current.isNull() || current.isMissingNode()) return;

        ObjectNode updated = mapper.createObjectNode();
        updated.put("container_id", containerId);
        updated.put("current_eggs", Math.max(0, current.path("current_eggs").asInt(0) + eggsDelta));
        updated.put("total_deposits", current.path("total_deposits").asInt(0) + depositTotal);
        updated.put("total_withdrawals", current.path("total_withdrawals").asInt(0) + withdrawalsDelta);
        updated.put("total_transfers_in", current.path("total_transfers_in").asInt(0) + transfersInDelta);
        updated.put("total_transfers_out", current.path("total_transfers_out").asInt(0) + transfersOutDelta);
        updated.put("total_consumed", current.path("total_consumed").asInt(0));
        updated.put("utilization_pct", current.path("utilization_pct").asDouble(0));

        updateProjection(API_PATH, meshqlId, updated);
    }

    private void updateContainerConsumed(String containerId, int eggs) {
        String meshqlId = cache.getContainerInventoryId(containerId);
        if (meshqlId == null) return;

        JsonNode current = getProjection(GRAPH_PATH, meshqlId, FIELDS);
        if (current == null || current.isNull() || current.isMissingNode()) return;

        ObjectNode updated = mapper.createObjectNode();
        updated.put("container_id", containerId);
        updated.put("current_eggs", current.path("current_eggs").asInt(0));
        updated.put("total_deposits", current.path("total_deposits").asInt(0));
        updated.put("total_withdrawals", current.path("total_withdrawals").asInt(0));
        updated.put("total_transfers_in", current.path("total_transfers_in").asInt(0));
        updated.put("total_transfers_out", current.path("total_transfers_out").asInt(0));
        updated.put("total_consumed", current.path("total_consumed").asInt(0) + eggs);
        updated.put("utilization_pct", current.path("utilization_pct").asDouble(0));

        updateProjection(API_PATH, meshqlId, updated);
    }
}
