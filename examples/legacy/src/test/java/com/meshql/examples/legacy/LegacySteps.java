package com.meshql.examples.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LegacySteps {
    private static final Logger logger = LoggerFactory.getLogger(LegacySteps.class);
    private final LegacyWorld world;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LegacySteps(LegacyWorld world) {
        this.world = world;
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
    }

    // ---- HTTP helpers ----

    private JsonNode restPost(String path, String jsonBody) throws IOException {
        HttpPost request = new HttpPost(LegacyWorld.apiBase + path);
        request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String body = EntityUtils.toString(response.getEntity());
            logger.debug("POST {} -> {}", path, body);
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

    // ---- Given steps ----

    @Given("the legacy API is available")
    public void apiIsAvailable() throws Exception {
        int maxAttempts = 30;
        for (int i = 1; i <= maxAttempts; i++) {
            try {
                HttpGet request = new HttpGet(LegacyWorld.apiBase + "/ready");
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int status = response.getCode();
                    if (status >= 200 && status < 300) {
                        logger.info("API is ready at {}", LegacyWorld.apiBase);
                        return;
                    }
                }
            } catch (Exception e) {
                // Connection refused — server not up yet
            }
            if (i == maxAttempts) {
                fail("API not ready after " + maxAttempts + " attempts at " + LegacyWorld.apiBase);
            }
            logger.info("Waiting for API... attempt {}/{}", i, maxAttempts);
            Thread.sleep(2000);
        }
    }

    @Given("the CDC pipeline has populated clean data")
    public void cdcPipelinePopulated() throws Exception {
        // Wait for CDC data to appear — poll for customers
        int maxAttempts = 30;
        for (int i = 1; i <= maxAttempts; i++) {
            try {
                JsonNode data = graphql("/customer/graph", "{ getAll { id } }");
                JsonNode customers = data.get("getAll");
                if (customers != null && customers.isArray() && customers.size() >= 4) {
                    logger.info("CDC pipeline populated: {} customers found", customers.size());
                    return;
                }
            } catch (Exception e) {
                // Not ready yet
            }
            if (i == maxAttempts) {
                fail("CDC pipeline did not populate data after " + maxAttempts + " attempts");
            }
            logger.info("Waiting for CDC data... attempt {}/{}", i, maxAttempts);
            Thread.sleep(3000);
        }
    }

    // ---- When steps: Customer queries ----

    @When("I query the customer graph for all customers")
    public void queryAllCustomers() throws Exception {
        JsonNode data = graphql("/customer/graph",
                "{ getAll { id account_number first_name last_name rate_class status } }");
        JsonNode arr = data.get("getAll");
        world.graphqlResults = new JsonNode[arr.size()];
        for (int i = 0; i < arr.size(); i++) world.graphqlResults[i] = arr.get(i);
    }

    @When("I query the customer graph for account {string}")
    public void queryCustomerByAccount(String accountNumber) throws Exception {
        JsonNode data = graphql("/customer/graph",
                String.format("{ getByAccountNumber(account_number: \"%s\") { id account_number first_name last_name address city state zip rate_class service_type status connected_date disconnected_date budget_billing paperless } }", accountNumber));
        world.graphqlResult = data.get("getByAccountNumber");
    }

    @When("I query the customer graph for account {string} with meter readings")
    public void queryCustomerWithReadings(String accountNumber) throws Exception {
        JsonNode data = graphql("/customer/graph",
                String.format("{ getByAccountNumber(account_number: \"%s\") { id first_name meterReadings { id reading_date reading_value reading_type estimated } } }", accountNumber));
        world.graphqlResult = data.get("getByAccountNumber");
    }

    @When("I query the customer graph for account {string} with bills")
    public void queryCustomerWithBills(String accountNumber) throws Exception {
        JsonNode data = graphql("/customer/graph",
                String.format("{ getByAccountNumber(account_number: \"%s\") { id first_name bills { id bill_date total_amount kwh_used status } } }", accountNumber));
        world.graphqlResult = data.get("getByAccountNumber");
    }

    @When("I query the customer graph for account {string} with payments")
    public void queryCustomerWithPayments(String accountNumber) throws Exception {
        JsonNode data = graphql("/customer/graph",
                String.format("{ getByAccountNumber(account_number: \"%s\") { id first_name payments { id payment_date amount method } } }", accountNumber));
        world.graphqlResult = data.get("getByAccountNumber");
    }

    @When("I query the customer graph for account {string} with full federation")
    public void queryCustomerFullFederation(String accountNumber) throws Exception {
        JsonNode data = graphql("/customer/graph",
                String.format("""
                { getByAccountNumber(account_number: "%s") {
                    id account_number first_name last_name rate_class status connected_date
                    meterReadings { id reading_date reading_value reading_type estimated }
                    bills { id bill_date total_amount kwh_used status }
                    payments { id payment_date amount method }
                } }""", accountNumber));
        world.graphqlResult = data.get("getByAccountNumber");
    }

    // ---- When steps: Other entity queries ----

    @When("I query the meter reading graph for all readings")
    public void queryAllMeterReadings() throws Exception {
        JsonNode data = graphql("/meter_reading/graph",
                "{ getAll { id reading_date reading_value reading_type estimated } }");
        JsonNode arr = data.get("getAll");
        world.graphqlResults = new JsonNode[arr.size()];
        for (int i = 0; i < arr.size(); i++) world.graphqlResults[i] = arr.get(i);
    }

    @When("I query the bill graph for all bills")
    public void queryAllBills() throws Exception {
        JsonNode data = graphql("/bill/graph",
                "{ getAll { id bill_date total_amount kwh_used status } }");
        JsonNode arr = data.get("getAll");
        world.graphqlResults = new JsonNode[arr.size()];
        for (int i = 0; i < arr.size(); i++) world.graphqlResults[i] = arr.get(i);
    }

    @When("I query the bill graph for paid bills with customer and payments")
    public void queryPaidBillsWithFederation() throws Exception {
        JsonNode data = graphql("/bill/graph",
                "{ getByStatus(status: \"paid\") { id bill_date total_amount customer { first_name last_name account_number } payments { id amount method } } }");
        JsonNode arr = data.get("getByStatus");
        world.graphqlResults = new JsonNode[arr.size()];
        for (int i = 0; i < arr.size(); i++) world.graphqlResults[i] = arr.get(i);
    }

    @When("I query the payment graph for all payments")
    public void queryAllPayments() throws Exception {
        JsonNode data = graphql("/payment/graph",
                "{ getAll { id payment_date amount method status } }");
        JsonNode arr = data.get("getAll");
        world.graphqlResults = new JsonNode[arr.size()];
        for (int i = 0; i < arr.size(); i++) world.graphqlResults[i] = arr.get(i);
    }

    // ---- Then steps: Customer assertions ----

    @Then("the customer results should contain a customer with first_name {string}")
    public void customerResultsContainFirstName(String expected) {
        assertContainsField("first_name", expected);
    }

    @Then("the customer results should contain a customer with last_name {string}")
    public void customerResultsContainLastName(String expected) {
        assertContainsField("last_name", expected);
    }

    @Then("the customer results should not contain a customer with first_name {string}")
    public void customerResultsNotContainFirstName(String unexpected) {
        assertNotNull(world.graphqlResults);
        for (JsonNode item : world.graphqlResults) {
            if (item.has("first_name") && unexpected.equals(item.get("first_name").asText())) {
                fail("Results should NOT contain first_name=" + unexpected);
            }
        }
    }

    // ---- Then steps: Meter reading assertions ----

    @Then("the meter reading results should contain a reading with reading_type {string}")
    public void meterReadingContainsType(String expected) {
        assertContainsField("reading_type", expected);
    }

    @Then("the meter reading results should contain a reading with estimated true")
    public void meterReadingContainsEstimatedTrue() {
        assertNotNull(world.graphqlResults);
        boolean found = false;
        for (JsonNode item : world.graphqlResults) {
            if (item.has("estimated") && item.get("estimated").asBoolean()) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Results should contain a reading with estimated=true");
    }

    // ---- Then steps: Bill assertions ----

    @Then("the bill results should contain a bill with total_amount {double}")
    public void billContainsAmount(double expected) {
        assertNotNull(world.graphqlResults);
        boolean found = false;
        for (JsonNode item : world.graphqlResults) {
            if (item.has("total_amount") && Math.abs(item.get("total_amount").asDouble() - expected) < 0.01) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Results should contain bill with total_amount=" + expected);
    }

    @Then("the bill results should not contain a bill with total_amount {double}")
    public void billNotContainsAmount(double unexpected) {
        assertNotNull(world.graphqlResults);
        for (JsonNode item : world.graphqlResults) {
            if (item.has("total_amount") && Math.abs(item.get("total_amount").asDouble() - unexpected) < 0.01) {
                fail("Results should NOT contain bill with total_amount=" + unexpected);
            }
        }
    }

    // ---- Then steps: Payment assertions ----

    @Then("the payment results should contain a payment with method {string}")
    public void paymentContainsMethod(String expected) {
        assertContainsField("method", expected);
    }

    @Then("the payment results should contain a payment with amount {double}")
    public void paymentContainsAmount(double expected) {
        assertNotNull(world.graphqlResults);
        boolean found = false;
        for (JsonNode item : world.graphqlResults) {
            if (item.has("amount") && Math.abs(item.get("amount").asDouble() - expected) < 0.01) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Results should contain payment with amount=" + expected);
    }

    @Then("the payment results should not contain a payment with amount {double}")
    public void paymentNotContainsAmount(double unexpected) {
        assertNotNull(world.graphqlResults);
        for (JsonNode item : world.graphqlResults) {
            if (item.has("amount") && Math.abs(item.get("amount").asDouble() - unexpected) < 0.01) {
                fail("Results should NOT contain payment with amount=" + unexpected);
            }
        }
    }

    // ---- Then steps: GraphQL result assertions ----

    @Then("the GraphQL result {string} should be {string}")
    public void graphqlResultString(String field, String expected) {
        assertNotNull(world.graphqlResult, "GraphQL result should not be null");
        assertTrue(world.graphqlResult.has(field), "Result should have field '" + field + "'");
        assertEquals(expected, world.graphqlResult.get(field).asText());
    }

    // ---- Then steps: Federation assertions ----

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

    @Then("the first bill result should have a nested {string} with {string} equal to {string}")
    public void firstBillResultNestedField(String objectField, String field, String expected) {
        assertNotNull(world.graphqlResults);
        assertTrue(world.graphqlResults.length > 0, "Should have at least one result");
        JsonNode nested = world.graphqlResults[0].get(objectField);
        assertNotNull(nested, "Nested object '" + objectField + "' should exist in first result");
        assertEquals(expected, nested.get(field).asText());
    }

    // ---- Helper ----

    private void assertContainsField(String field, String expected) {
        assertNotNull(world.graphqlResults, "Results should not be null");
        boolean found = false;
        for (JsonNode item : world.graphqlResults) {
            if (item.has(field) && expected.equals(item.get(field).asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Results should contain item with " + field + "=" + expected);
    }
}
