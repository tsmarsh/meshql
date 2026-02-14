package com.meshql.examples.egg_economy_salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.egg_economy_salesforce.FarmTransformer.*;

/**
 * Transforms Salesforce Container_Transfer__c rows into clean ContainerTransfer entities.
 *
 * SF columns: Id, Source_Container__c(FK), Dest_Container__c(FK), Egg_Count__c, Transport_Method__c, CreatedDate
 * Clean fields: source_container_id, dest_container_id, eggs, timestamp, transport_method, legacy_sf_id
 */
public class ContainerTransferTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(ContainerTransferTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> TRANSPORT_MAP = Map.of(
            "Truck", "truck",
            "Van", "van",
            "Cart", "cart"
    );

    private final IdResolver idResolver;

    public ContainerTransferTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        clean.put("legacy_sf_id", textOrNull(row, "id"));

        // Resolve source container FK
        String sourceSfId = textOrNull(row, "source_container__c");
        String sourceContainerId = idResolver.resolveContainerId(sourceSfId);
        if (sourceContainerId != null) {
            clean.put("source_container_id", sourceContainerId);
        }

        // Resolve dest container FK
        String destSfId = textOrNull(row, "dest_container__c");
        String destContainerId = idResolver.resolveContainerId(destSfId);
        if (destContainerId != null) {
            clean.put("dest_container_id", destContainerId);
        }

        if (row.has("egg_count__c") && !row.get("egg_count__c").isNull()) {
            clean.put("eggs", row.get("egg_count__c").asInt());
        }

        putIfPresent(clean, "timestamp", textOrNull(row, "createddate"));

        String transport = textOrNull(row, "transport_method__c");
        clean.put("transport_method", TRANSPORT_MAP.getOrDefault(transport, transport != null ? transport.toLowerCase() : "truck"));

        logger.debug("Transformed container transfer: SF Id={}, eggs={}", clean.get("legacy_sf_id"), clean.get("eggs"));
        return clean;
    }
}
