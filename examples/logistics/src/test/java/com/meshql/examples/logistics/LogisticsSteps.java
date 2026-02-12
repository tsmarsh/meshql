package com.meshql.examples.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LogisticsSteps {
    private static final Logger logger = LoggerFactory.getLogger(LogisticsSteps.class);
    private final LogisticsWorld world;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LogisticsSteps(LogisticsWorld world) {
        this.world = world;
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
    }

    // ---- HTTP helpers ----

    private JsonNode restPost(String path, String jsonBody) throws IOException {
        HttpPost request = new HttpPost(LogisticsWorld.apiBase + path);
        request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String body = EntityUtils.toString(response.getEntity());
            logger.debug("POST {} -> {}", path, body);
            return objectMapper.readTree(body);
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    private JsonNode restGet(String path) throws IOException {
        HttpGet request = new HttpGet(LogisticsWorld.apiBase + path);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String body = EntityUtils.toString(response.getEntity());
            logger.debug("GET {} -> {}", path, body);
            return objectMapper.readTree(body);
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    private JsonNode graphql(String endpoint, String query) throws IOException {
        String requestBody = objectMapper.writeValueAsString(Map.of("query", query));
        JsonNode response = restPost(endpoint, requestBody);
        if (response.has("errors") && !response.get("errors").isNull()) {
            fail("GraphQL error: " + response.get("errors"));
        }
        return response.get("data");
    }

    /** Look up an entity's ID via GraphQL after REST creation. */
    private String lookupId(String graphEndpoint, String queryName, String query) throws IOException {
        JsonNode data = graphql(graphEndpoint, query);
        JsonNode result = data.get(queryName);
        if (result.isArray()) {
            assertTrue(result.size() > 0, "Expected at least 1 result from " + queryName);
            return result.get(0).get("id").asText();
        }
        assertNotNull(result, queryName + " returned null");
        return result.get("id").asText();
    }

    // ---- Given steps ----

    @Given("the logistics API is available")
    public void apiIsAvailable() throws Exception {
        int maxAttempts = 30;
        for (int i = 1; i <= maxAttempts; i++) {
            try {
                HttpGet request = new HttpGet(LogisticsWorld.apiBase + "/ready");
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int status = response.getCode();
                    if (status >= 200 && status < 300) {
                        logger.info("API is ready at {}", LogisticsWorld.apiBase);
                        return;
                    }
                }
            } catch (Exception e) {
                // Connection refused — server not up yet
            }
            if (i == maxAttempts) {
                fail("API not ready after " + maxAttempts + " attempts at " + LogisticsWorld.apiBase);
            }
            logger.info("Waiting for API... attempt {}/{}", i, maxAttempts);
            Thread.sleep(2000);
        }
    }

    // ---- When steps: REST creation (IDs looked up via GraphQL) ----

    @When("I create a warehouse via REST:")
    public void createWarehouse(DataTable table) throws Exception {
        Map<String, String> row = table.asMaps().get(0);
        String json = objectMapper.writeValueAsString(Map.of(
                "name", row.get("name"),
                "address", row.get("address"),
                "city", row.get("city"),
                "state", row.get("state"),
                "zip", row.get("zip"),
                "capacity", Integer.parseInt(row.get("capacity"))
        ));
        restPost("/warehouse/api", json);
        // Look up the ID via GraphQL (unique by name)
        String name = row.get("name");
        String city = row.get("city");
        JsonNode data = graphql("/warehouse/graph",
                String.format("{ getByCity(city: \"%s\") { id name } }", city));
        JsonNode arr = data.get("getByCity");
        for (JsonNode w : arr) {
            if (name.equals(w.get("name").asText())) {
                LogisticsWorld.warehouseId1 = w.get("id").asText();
                break;
            }
        }
        logger.info("Created warehouse 1: {}", LogisticsWorld.warehouseId1);
    }

    @When("I create a second warehouse via REST:")
    public void createSecondWarehouse(DataTable table) throws Exception {
        Map<String, String> row = table.asMaps().get(0);
        String json = objectMapper.writeValueAsString(Map.of(
                "name", row.get("name"),
                "address", row.get("address"),
                "city", row.get("city"),
                "state", row.get("state"),
                "zip", row.get("zip"),
                "capacity", Integer.parseInt(row.get("capacity"))
        ));
        restPost("/warehouse/api", json);
        String name = row.get("name");
        String city = row.get("city");
        JsonNode data = graphql("/warehouse/graph",
                String.format("{ getByCity(city: \"%s\") { id name } }", city));
        JsonNode arr = data.get("getByCity");
        for (JsonNode w : arr) {
            if (name.equals(w.get("name").asText())) {
                LogisticsWorld.warehouseId2 = w.get("id").asText();
                break;
            }
        }
        logger.info("Created warehouse 2: {}", LogisticsWorld.warehouseId2);
    }

    @When("I create a shipment for the first warehouse:")
    public void createShipmentForFirstWarehouse(DataTable table) throws Exception {
        Map<String, String> row = table.asMaps().get(0);
        String json = objectMapper.writeValueAsString(Map.of(
                "destination", row.get("destination"),
                "carrier", row.get("carrier"),
                "status", row.get("status"),
                "estimated_delivery", row.get("estimated_delivery"),
                "warehouse_id", LogisticsWorld.warehouseId1
        ));
        restPost("/shipment/api", json);
        // Look up ID via GraphQL
        String dest = row.get("destination");
        JsonNode data = graphql("/shipment/graph",
                String.format("{ getByWarehouse(id: \"%s\") { id destination } }", LogisticsWorld.warehouseId1));
        for (JsonNode s : data.get("getByWarehouse")) {
            if (dest.equals(s.get("destination").asText())) {
                LogisticsWorld.shipmentId1 = s.get("id").asText();
                break;
            }
        }
        logger.info("Created shipment 1: {}", LogisticsWorld.shipmentId1);
    }

    @When("I create a shipment for the second warehouse:")
    public void createShipmentForSecondWarehouse(DataTable table) throws Exception {
        Map<String, String> row = table.asMaps().get(0);
        String json = objectMapper.writeValueAsString(Map.of(
                "destination", row.get("destination"),
                "carrier", row.get("carrier"),
                "status", row.get("status"),
                "estimated_delivery", row.get("estimated_delivery"),
                "warehouse_id", LogisticsWorld.warehouseId2
        ));
        restPost("/shipment/api", json);
        String dest = row.get("destination");
        JsonNode data = graphql("/shipment/graph",
                String.format("{ getByWarehouse(id: \"%s\") { id destination } }", LogisticsWorld.warehouseId2));
        for (JsonNode s : data.get("getByWarehouse")) {
            if (dest.equals(s.get("destination").asText())) {
                LogisticsWorld.shipmentId2 = s.get("id").asText();
                break;
            }
        }
        logger.info("Created shipment 2: {}", LogisticsWorld.shipmentId2);
    }

    @When("I create a package in the first shipment:")
    public void createPackageInFirstShipment(DataTable table) throws Exception {
        Map<String, String> row = table.asMaps().get(0);
        String tn = row.get("tracking_number");
        String json = objectMapper.writeValueAsString(Map.of(
                "tracking_number", tn,
                "description", row.get("description"),
                "weight", Double.parseDouble(row.get("weight")),
                "recipient", row.get("recipient"),
                "recipient_address", row.get("recipient_address"),
                "warehouse_id", LogisticsWorld.warehouseId1,
                "shipment_id", LogisticsWorld.shipmentId1
        ));
        restPost("/package/api", json);
        LogisticsWorld.packageId1 = lookupId("/package/graph", "getByTrackingNumber",
                String.format("{ getByTrackingNumber(tracking_number: \"%s\") { id } }", tn));
        logger.info("Created package 1: {}", LogisticsWorld.packageId1);
    }

    @When("I create a package in the second shipment:")
    public void createPackageInSecondShipment(DataTable table) throws Exception {
        Map<String, String> row = table.asMaps().get(0);
        String tn = row.get("tracking_number");
        String json = objectMapper.writeValueAsString(Map.of(
                "tracking_number", tn,
                "description", row.get("description"),
                "weight", Double.parseDouble(row.get("weight")),
                "recipient", row.get("recipient"),
                "recipient_address", row.get("recipient_address"),
                "warehouse_id", LogisticsWorld.warehouseId2,
                "shipment_id", LogisticsWorld.shipmentId2
        ));
        restPost("/package/api", json);
        LogisticsWorld.packageId2 = lookupId("/package/graph", "getByTrackingNumber",
                String.format("{ getByTrackingNumber(tracking_number: \"%s\") { id } }", tn));
        logger.info("Created package 2: {}", LogisticsWorld.packageId2);
    }

    @When("I create tracking updates for the first package:")
    public void createTrackingUpdatesForFirstPackage(DataTable table) throws Exception {
        createTrackingUpdates(LogisticsWorld.packageId1, table);
    }

    @When("I create tracking updates for the second package:")
    public void createTrackingUpdatesForSecondPackage(DataTable table) throws Exception {
        createTrackingUpdates(LogisticsWorld.packageId2, table);
    }

    private void createTrackingUpdates(String packageId, DataTable table) throws Exception {
        List<Map<String, String>> rows = table.asMaps();
        for (Map<String, String> row : rows) {
            String json = objectMapper.writeValueAsString(Map.of(
                    "package_id", packageId,
                    "status", row.get("status"),
                    "location", row.get("location"),
                    "timestamp", row.get("timestamp"),
                    "notes", row.get("notes")
            ));
            restPost("/tracking_update/api", json);
            logger.debug("Created tracking update: {} at {}", row.get("status"), row.get("location"));
        }
    }

    // ---- When steps: REST reads ----

    @When("I GET the first warehouse via REST")
    public void getFirstWarehouse() throws Exception {
        world.restResponse = restGet("/warehouse/api/" + LogisticsWorld.warehouseId1);
    }

    @When("I GET all warehouses via REST")
    public void getAllWarehouses() throws Exception {
        world.restResponse = restGet("/warehouse/api");
    }

    // ---- When steps: GraphQL queries ----

    @When("I query the warehouse graph for the first warehouse by ID")
    public void queryWarehouseById() throws Exception {
        String query = String.format(
                "{ getById(id: \"%s\") { id name address city state zip capacity } }",
                LogisticsWorld.warehouseId1
        );
        JsonNode data = graphql("/warehouse/graph", query);
        world.graphqlResult = data.get("getById");
    }

    @When("I query the warehouse graph by city {string}")
    public void queryWarehouseByCity(String city) throws Exception {
        String query = String.format(
                "{ getByCity(city: \"%s\") { id name city state } }", city
        );
        JsonNode data = graphql("/warehouse/graph", query);
        JsonNode arr = data.get("getByCity");
        world.graphqlResults = new JsonNode[arr.size()];
        for (int i = 0; i < arr.size(); i++) world.graphqlResults[i] = arr.get(i);
    }

    @When("I query the package graph by tracking number {string}")
    public void queryPackageByTrackingNumber(String tn) throws Exception {
        String query = String.format(
                "{ getByTrackingNumber(tracking_number: \"%s\") { id tracking_number description weight recipient recipient_address } }",
                tn
        );
        JsonNode data = graphql("/package/graph", query);
        world.graphqlResult = data.get("getByTrackingNumber");
    }

    @When("I query the shipment graph by status {string}")
    public void queryShipmentByStatus(String status) throws Exception {
        String query = String.format(
                "{ getByStatus(status: \"%s\") { id destination carrier status } }", status
        );
        JsonNode data = graphql("/shipment/graph", query);
        JsonNode arr = data.get("getByStatus");
        world.graphqlResults = new JsonNode[arr.size()];
        for (int i = 0; i < arr.size(); i++) world.graphqlResults[i] = arr.get(i);
    }

    // ---- When steps: Federation queries ----

    @When("I query the warehouse graph with nested shipments for the first warehouse")
    public void queryWarehouseWithShipments() throws Exception {
        String query = String.format(
                "{ getById(id: \"%s\") { name city state shipments { id destination carrier status estimated_delivery } } }",
                LogisticsWorld.warehouseId1
        );
        JsonNode data = graphql("/warehouse/graph", query);
        world.graphqlResult = data.get("getById");
    }

    @When("I query the warehouse graph with nested packages for the first warehouse")
    public void queryWarehouseWithPackages() throws Exception {
        String query = String.format(
                "{ getById(id: \"%s\") { name packages { id tracking_number description recipient } } }",
                LogisticsWorld.warehouseId1
        );
        JsonNode data = graphql("/warehouse/graph", query);
        world.graphqlResult = data.get("getById");
    }

    @When("I query the shipment graph with nested warehouse and packages for the first shipment")
    public void queryShipmentWithFederation() throws Exception {
        String query = String.format(
                "{ getById(id: \"%s\") { destination carrier status warehouse { name city state } packages { id tracking_number description } } }",
                LogisticsWorld.shipmentId1
        );
        JsonNode data = graphql("/shipment/graph", query);
        world.graphqlResult = data.get("getById");
    }

    @When("I query the package graph with full federation for {string}")
    public void queryPackageFullFederation(String tn) throws Exception {
        String query = String.format("""
                { getByTrackingNumber(tracking_number: "%s") {
                    id tracking_number description weight recipient recipient_address
                    warehouse { id name city state }
                    shipment { id destination carrier status estimated_delivery }
                    trackingUpdates { id status location timestamp notes }
                } }""", tn);
        JsonNode data = graphql("/package/graph", query);
        world.graphqlResult = data.get("getByTrackingNumber");
    }

    @When("I query the tracking update graph for the first package")
    public void queryTrackingUpdatesByPackage() throws Exception {
        String query = String.format(
                "{ getByPackage(id: \"%s\") { id status location timestamp notes package { id tracking_number } } }",
                LogisticsWorld.packageId1
        );
        JsonNode data = graphql("/tracking_update/graph", query);
        JsonNode arr = data.get("getByPackage");
        world.graphqlResults = new JsonNode[arr.size()];
        for (int i = 0; i < arr.size(); i++) world.graphqlResults[i] = arr.get(i);
    }

    // ---- Then steps: Assertions ----

    @Then("the first warehouse ID should be set")
    public void firstWarehouseIdSet() {
        assertNotNull(LogisticsWorld.warehouseId1, "Warehouse 1 ID should be set");
        assertFalse(LogisticsWorld.warehouseId1.isEmpty());
    }

    @Then("the first package ID should be set")
    public void firstPackageIdSet() {
        assertNotNull(LogisticsWorld.packageId1, "Package 1 ID should be set");
        assertFalse(LogisticsWorld.packageId1.isEmpty());
    }

    @Then("the response {string} should be {string}")
    public void restResponseFieldString(String field, String expected) {
        assertNotNull(world.restResponse, "REST response should not be null");
        assertEquals(expected, world.restResponse.get(field).asText());
    }

    @Then("the response {string} should be {int}")
    public void restResponseFieldInt(String field, int expected) {
        assertNotNull(world.restResponse);
        assertEquals(expected, world.restResponse.get(field).asInt());
    }

    @Then("the list should contain at least {int} items")
    public void listContainsAtLeast(int min) {
        assertNotNull(world.restResponse);
        assertTrue(world.restResponse.isArray(), "Response should be an array");
        assertTrue(world.restResponse.size() >= min,
                "Expected at least " + min + " items, got " + world.restResponse.size());
    }

    @Then("the list should contain an item with {string} equal to {string}")
    public void listContainsItemWithField(String field, String expected) {
        assertNotNull(world.restResponse);
        boolean found = false;
        for (JsonNode item : world.restResponse) {
            if (item.has(field) && expected.equals(item.get(field).asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "List should contain item with " + field + "=" + expected);
    }

    @Then("the GraphQL result {string} should be {string}")
    public void graphqlResultString(String field, String expected) {
        assertNotNull(world.graphqlResult, "GraphQL result should not be null");
        assertTrue(world.graphqlResult.has(field), "Result should have field '" + field + "'");
        assertEquals(expected, world.graphqlResult.get(field).asText());
    }

    @Then("the GraphQL result {string} should be {double}")
    public void graphqlResultDouble(String field, double expected) {
        assertNotNull(world.graphqlResult);
        assertEquals(expected, world.graphqlResult.get(field).asDouble(), 0.01);
    }

    @Then("the GraphQL results should contain at least {int} item(s)")
    public void graphqlResultsContainAtLeast(int min) {
        assertNotNull(world.graphqlResults, "GraphQL results should not be null");
        assertTrue(world.graphqlResults.length >= min,
                "Expected at least " + min + " results, got " + world.graphqlResults.length);
    }

    @Then("the GraphQL results should contain an item with {string} equal to {string}")
    public void graphqlResultsContainItemWithField(String field, String expected) {
        assertNotNull(world.graphqlResults);
        boolean found = false;
        for (JsonNode item : world.graphqlResults) {
            if (item.has(field) && expected.equals(item.get(field).asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Results should contain item with " + field + "=" + expected);
    }

    @Then("the nested {string} should contain at least {int} item(s)")
    public void nestedArrayContainsAtLeast(String field, int min) {
        assertNotNull(world.graphqlResult);
        JsonNode arr = world.graphqlResult.get(field);
        assertNotNull(arr, "Nested field '" + field + "' should exist");
        assertTrue(arr.isArray(), field + " should be an array");
        assertTrue(arr.size() >= min,
                "Expected at least " + min + " in " + field + ", got " + arr.size());
    }

    @Then("the nested {string} should contain an item with {string} equal to {string}")
    public void nestedArrayContainsItem(String arrayField, String itemField, String expected) {
        assertNotNull(world.graphqlResult);
        JsonNode arr = world.graphqlResult.get(arrayField);
        assertNotNull(arr, "Nested field '" + arrayField + "' should exist");
        boolean found = false;
        for (JsonNode item : arr) {
            if (item.has(itemField) && expected.equals(item.get(itemField).asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, arrayField + " should contain item with " + itemField + "=" + expected);
    }

    @Then("the nested {string} field {string} should be {string}")
    public void nestedObjectFieldString(String objectField, String field, String expected) {
        assertNotNull(world.graphqlResult);
        JsonNode nested = world.graphqlResult.get(objectField);
        assertNotNull(nested, "Nested object '" + objectField + "' should exist");
        assertEquals(expected, nested.get(field).asText());
    }

    @Then("the first result nested {string} field {string} should be {string}")
    public void firstResultNestedField(String objectField, String field, String expected) {
        assertNotNull(world.graphqlResults);
        assertTrue(world.graphqlResults.length > 0);
        JsonNode nested = world.graphqlResults[0].get(objectField);
        assertNotNull(nested, "Nested object '" + objectField + "' should exist in first result");
        assertEquals(expected, nested.get(field).asText());
    }
}
