package com.meshql.auth.jwt;

import com.meshql.core.Envelope;
import com.tailoredshapes.stash.Stash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JWTSubAuthorizer
 */
class JWTSubAuthorizerTest {
    private JWTSubAuthorizer authorizer;
    private static final String TEST_SUB = "test-user";

    @BeforeEach
    void setUp() {
        authorizer = new JWTSubAuthorizer();
    }

    /**
     * Create a JWT token with the given subject.
     * Note: This creates a valid JWT structure but with no signature verification
     * (signature is just a placeholder), which matches our use case.
     */
    private String createTestToken(String subject) {
        // Header: {"alg":"HS256","typ":"JWT"}
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());

        // Payload with sub claim
        String payloadJson = String.format("{\"sub\":\"%s\",\"iat\":1234567890}", subject);
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes());

        // Signature (placeholder - we don't verify signatures)
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("signature".getBytes());

        return header + "." + payload + "." + signature;
    }

    // ==================== getAuthToken tests ====================

    @Test
    void getAuthToken_shouldReturnEmptyList_whenNoHeaders() {
        Stash context = new Stash();

        List<String> result = authorizer.getAuthToken(context);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAuthToken_shouldReturnEmptyList_whenHeadersIsNull() {
        Stash context = new Stash();
        context.put("headers", null);

        List<String> result = authorizer.getAuthToken(context);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAuthToken_shouldReturnEmptyList_whenNoAuthorizationHeader() {
        Stash context = new Stash();
        context.put("headers", Map.of("content-type", "application/json"));

        List<String> result = authorizer.getAuthToken(context);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAuthToken_shouldReturnEmptyList_whenAuthorizationIsNotBearer() {
        Stash context = new Stash();
        context.put("headers", Map.of("authorization", "Basic sometoken"));

        List<String> result = authorizer.getAuthToken(context);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAuthToken_shouldExtractSubject_fromValidBearerToken() {
        String token = createTestToken(TEST_SUB);
        Stash context = new Stash();
        context.put("headers", Map.of("authorization", "Bearer " + token));

        List<String> result = authorizer.getAuthToken(context);

        assertEquals(1, result.size());
        assertEquals(TEST_SUB, result.get(0));
    }

    @Test
    void getAuthToken_shouldWorkWithStashHeaders() {
        String token = createTestToken(TEST_SUB);
        Stash headers = new Stash();
        headers.put("authorization", "Bearer " + token);

        Stash context = new Stash();
        context.put("headers", headers);

        List<String> result = authorizer.getAuthToken(context);

        assertEquals(1, result.size());
        assertEquals(TEST_SUB, result.get(0));
    }

    @Test
    void getAuthToken_shouldReturnEmptyList_whenTokenHasInvalidFormat() {
        Stash context = new Stash();
        context.put("headers", Map.of("authorization", "Bearer invalid-token-no-dots"));

        List<String> result = authorizer.getAuthToken(context);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAuthToken_shouldReturnEmptyList_whenTokenPayloadIsInvalidJson() {
        // Create token with invalid base64 payload
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\"}".getBytes());
        String invalidPayload = "not-valid-base64!!!";
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("sig".getBytes());

        String token = header + "." + invalidPayload + "." + signature;

        Stash context = new Stash();
        context.put("headers", Map.of("authorization", "Bearer " + token));

        List<String> result = authorizer.getAuthToken(context);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAuthToken_shouldReturnEmptyList_whenTokenHasNoSubClaim() {
        // Create token without sub claim
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"iat\":1234567890}".getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("sig".getBytes());

        String token = header + "." + payload + "." + signature;

        Stash context = new Stash();
        context.put("headers", Map.of("authorization", "Bearer " + token));

        List<String> result = authorizer.getAuthToken(context);

        assertTrue(result.isEmpty());
    }

    // ==================== isAuthorized tests ====================

    @Test
    void isAuthorized_shouldReturnTrue_whenAuthorizedTokensIsNull() {
        Envelope envelope = new Envelope("id", new Stash(), Instant.now(), false, null);

        boolean result = authorizer.isAuthorized(List.of("some-cred"), envelope);

        assertTrue(result);
    }

    @Test
    void isAuthorized_shouldReturnTrue_whenAuthorizedTokensIsEmpty() {
        Envelope envelope = new Envelope("id", new Stash(), Instant.now(), false, List.of());

        boolean result = authorizer.isAuthorized(List.of("some-cred"), envelope);

        assertTrue(result);
    }

    @Test
    void isAuthorized_shouldReturnTrue_whenCredentialsMatchAuthorizedTokens() {
        Envelope envelope = new Envelope("id", new Stash(), Instant.now(), false, List.of("user1", "user2"));

        boolean result = authorizer.isAuthorized(List.of("user1"), envelope);

        assertTrue(result);
    }

    @Test
    void isAuthorized_shouldReturnTrue_whenAnyCredentialMatches() {
        Envelope envelope = new Envelope("id", new Stash(), Instant.now(), false, List.of("user1", "user2"));

        boolean result = authorizer.isAuthorized(List.of("other", "user2"), envelope);

        assertTrue(result);
    }

    @Test
    void isAuthorized_shouldReturnFalse_whenNoCredentialsMatch() {
        Envelope envelope = new Envelope("id", new Stash(), Instant.now(), false, List.of("user1", "user2"));

        boolean result = authorizer.isAuthorized(List.of("user3"), envelope);

        assertFalse(result);
    }

    @Test
    void isAuthorized_shouldReturnFalse_whenCredentialsIsEmpty() {
        Envelope envelope = new Envelope("id", new Stash(), Instant.now(), false, List.of("user1"));

        boolean result = authorizer.isAuthorized(List.of(), envelope);

        assertFalse(result);
    }
}
