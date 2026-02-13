package com.meshql.mesher.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class DomainModelTest {

    @Test
    void deserializeSpringfieldDomainModel() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass().getResourceAsStream("/springfield-domain-model.json");
        assertNotNull(is, "Fixture file not found");

        DomainModel model = mapper.readValue(is, DomainModel.class);

        assertEquals("springfield-electric", model.projectName());
        assertEquals("com.meshql.examples.legacy", model.packageName());
        assertEquals(4066, model.port());
        assertEquals("legacy", model.prefix());
        assertEquals("springfield_electric", model.legacyDbName());
        assertEquals(4, model.entities().size());
        assertEquals(3, model.processingPhases().size());
    }

    @Test
    void customerEntityIsCorrect() throws Exception {
        DomainModel model = loadFixture();
        EntityModel customer = model.entities().get(0);

        assertEquals("cust_acct", customer.legacyTable());
        assertEquals("customer", customer.cleanName());
        assertEquals("Customer", customer.className());
        assertEquals("acct_id", customer.legacyPrimaryKey());
        assertEquals("legacy_acct_id", customer.legacyIdField());
        assertTrue(customer.isRoot());
        assertEquals(1, customer.processingPhase());
        assertEquals(16, customer.fields().size());
        assertEquals(3, customer.codeMaps().size());
        assertEquals(3, customer.relationships().children().size());
        assertEquals(0, customer.relationships().parents().size());
    }

    @Test
    void processingPhasesAreCorrect() throws Exception {
        DomainModel model = loadFixture();

        ProcessingPhase phase1 = model.processingPhases().get(0);
        assertEquals(1, phase1.phase());
        assertEquals(1, phase1.entities().size());
        assertEquals("cust_acct", phase1.entities().get(0));
        assertEquals(1, phase1.cachePopulation().size());
        assertEquals("customer", phase1.cachePopulation().get(0));

        ProcessingPhase phase3 = model.processingPhases().get(2);
        assertEquals(3, phase3.phase());
        assertEquals(2, phase3.entities().size());
    }

    @Test
    void roundTripSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DomainModel model = loadFixture();

        String json = mapper.writeValueAsString(model);
        DomainModel deserialized = mapper.readValue(json, DomainModel.class);

        assertEquals(model.projectName(), deserialized.projectName());
        assertEquals(model.entities().size(), deserialized.entities().size());
        assertEquals(model.entities().get(0).fields().size(), deserialized.entities().get(0).fields().size());
    }

    private DomainModel loadFixture() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass().getResourceAsStream("/springfield-domain-model.json");
        return mapper.readValue(is, DomainModel.class);
    }
}
