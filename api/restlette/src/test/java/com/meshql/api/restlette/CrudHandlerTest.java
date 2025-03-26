package com.meshql.api.restlette;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meshql.auth.noop.NoAuth;
import com.meshql.core.Auth;
import com.meshql.core.Envelope;
import com.meshql.core.Repository;
import com.meshql.core.Validator;
import com.meshql.repositories.memory.InMemoryRepository;
import com.tailoredshapes.stash.Stash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spark.Request;
import spark.Response;

import java.time.Instant;
import java.util.*;

import static com.tailoredshapes.stash.Stash.stash;
import static com.tailoredshapes.underbar.ocho.UnderBar.list;
import static com.tailoredshapes.underbar.ocho.UnderBar.map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrudHandlerTest {
    private static final String API_PATH = "/hens";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private Repository repository;
    private Auth auth;
    private Validator validator;
    private CrudHandler crudHandler;
    private Request mockRequest;
    private Response mockResponse;
    
    private static final Stash henSchema = stash(
            "type", "object",
            "properties", stash(
                    "name", stash("type", "string", "minLength", 1),
                    "eggs", stash("type", "integer", "minimum", 0)),
            "required", list("name", "eggs"),
            "additionalProperties", false);

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        auth = new NoAuth();
        validator = new JSONSchemaValidator(henSchema);
        crudHandler = new CrudHandler(auth, repository, validator, List.of());
        
        mockRequest = mock(Request.class);
        mockResponse = mock(Response.class);
    }

    @Test
    void testBasicCRUDOperations() throws Exception {
        // Test Create
        var payload = stash("name","Henny","eggs", 5);
        String henData = payload.toJSONString();
        when(mockRequest.body()).thenReturn(henData);
        
        Stash createResult = crudHandler.create(mockRequest, mockResponse);
        
        verify(mockResponse).status(201);
        assertEquals("Henny", createResult.get("name"));
        assertEquals(5.0, createResult.get("eggs"));

        
        // Get the ID from the repository
        Envelope envelope = repository.list(list()).get(0);
        String henId = envelope.id();

        // Test Read
        when(mockRequest.params(":id")).thenReturn(henId);
        
        Stash readResult = crudHandler.read(mockRequest, mockResponse);
        
        assertEquals("Henny", readResult.get("name"));
        assertEquals(5.0, readResult.get("eggs"));
        
        // Test Update
        String updatedHenData = "{\"name\":\"Henny\",\"eggs\":10}";
        when(mockRequest.body()).thenReturn(updatedHenData);
        
        Stash updateResult = crudHandler.update(mockRequest, mockResponse);
        
        assertEquals("Henny", updateResult.get("name"));
        assertEquals(10.0, updateResult.get("eggs"));
        
        // Test Delete
        Stash deleteResult = crudHandler.remove(mockRequest, mockResponse);
        
        assertEquals(henId, deleteResult.get("id"));
        assertEquals("deleted", deleteResult.get("status"));
        
        // Verify delete by trying to read again
        Stash readAfterDeleteResult = crudHandler.read(mockRequest, mockResponse);
        
        verify(mockResponse, times(1)).status(404);
        assertTrue(readAfterDeleteResult.containsKey("error"));
    }

    @Test
    void testNegativeCases() throws Exception {
        // Test invalid ID
        when(mockRequest.params(":id")).thenReturn("999");
        
        Stash invalidReadResult = crudHandler.read(mockRequest, mockResponse);
        
        verify(mockResponse).status(404);
        assertTrue(invalidReadResult.containsKey("error"));
        
        // Test invalid payload for creation
        String invalidHenData = "{\"eggs\":\"not a number\"}";
        when(mockRequest.body()).thenReturn(invalidHenData);
        
        Stash invalidCreateResult = crudHandler.create(mockRequest, mockResponse);
        
        verify(mockResponse).status(400);
        assertTrue(invalidCreateResult.containsKey("error"));
    }

    @Test
    void testAuthorizationScenarios() throws Exception {
        // Pre-populate a test hen
        String henId = "666";
        Envelope hen = new Envelope(
                henId,
                stash("name", "chuck", "eggs", 6),
                Instant.now(),
                false,
                List.of("token"));
        repository.create(hen, list());
        
        // Test with valid token
        when(mockRequest.params(":id")).thenReturn(henId);
        when(mockRequest.headers("Authorization")).thenReturn("token");
        
        Stash authorizedResult = crudHandler.read(mockRequest, mockResponse);
        
        assertEquals("chuck", authorizedResult.get("name"));
        assertEquals(6, authorizedResult.get("eggs"));
        
        // Test with invalid token (NoAuth implementation doesn't actually check tokens)
        when(mockRequest.headers("Authorization")).thenReturn("invalidToken");
        
        Stash unauthorizedResult = crudHandler.read(mockRequest, mockResponse);
        
        // With NoAuth, this should still work
        assertEquals("chuck", unauthorizedResult.get("name"));
    }

    @Test
    void testBulkOperations() throws Exception {
        // Test bulk create
        String bulkHenData = "[{\"name\":\"Hen1\",\"eggs\":1},{\"name\":\"Hen2\",\"eggs\":2}]";
        when(mockRequest.body()).thenReturn(bulkHenData);
        
        Object bulkCreateResult = crudHandler.bulkCreate(mockRequest, mockResponse);
        
        verify(mockResponse).status(201);
        
        // Verify repository has 2 items
        List<Envelope> envelopes = repository.list(list());
        assertEquals(2, envelopes.size());
        
        // Test bulk read
        List<String> henIds = map(envelopes, (e) -> e.id());
        
        when(mockRequest.queryParams("ids")).thenReturn(String.join(",", henIds));
        
        Object bulkReadResult = crudHandler.bulkRead(mockRequest, mockResponse);
        
        // Convert the result to a String regardless of its type
        String resultJson = String.valueOf(bulkReadResult);
        assertTrue(resultJson.contains("\"name\":\"Hen1\""));
        assertTrue(resultJson.contains("\"name\":\"Hen2\""));
    }
}