package com.meshql.examples.egg_economy_salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Transforms a legacy SAP database row (from Debezium CDC) into a clean domain object
 * suitable for the MeshQL REST API.
 */
public interface LegacyTransformer {
    ObjectNode transform(JsonNode legacyRow);
}
