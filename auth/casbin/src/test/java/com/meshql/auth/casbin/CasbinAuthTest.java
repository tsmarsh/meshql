package com.meshql.auth.casbin;

import com.meshql.core.Auth;
import com.meshql.core.Envelope;
import com.tailoredshapes.stash.Stash;
import org.casbin.jcasbin.main.Enforcer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CasbinAuth
 */
@ExtendWith(MockitoExtension.class)
class CasbinAuthTest {

    @Mock
    private Auth mockWrappedAuth;

    private Enforcer enforcer;
    private CasbinAuth casbinAuth;

    @BeforeEach
    void setUp() {
        // Load test model and policy from resources
        String modelPath = getClass().getClassLoader().getResource("model.conf").getPath();
        String policyPath = getClass().getClassLoader().getResource("policy.csv").getPath();

        enforcer = new Enforcer(modelPath, policyPath);
        casbinAuth = new CasbinAuth(enforcer, mockWrappedAuth);
    }

    // ==================== getAuthToken tests ====================

    @Test
    void getAuthToken_shouldReturnRoles_forUserWithRoles() {
        // user1 has role 'admin' in policy.csv
        when(mockWrappedAuth.getAuthToken(any())).thenReturn(List.of("user1"));

        List<String> roles = casbinAuth.getAuthToken(new Stash());

        assertEquals(1, roles.size());
        assertTrue(roles.contains("admin"));
    }

    @Test
    void getAuthToken_shouldReturnEditorRole_forUser2() {
        // user2 has role 'editor' in policy.csv
        when(mockWrappedAuth.getAuthToken(any())).thenReturn(List.of("user2"));

        List<String> roles = casbinAuth.getAuthToken(new Stash());

        assertEquals(1, roles.size());
        assertTrue(roles.contains("editor"));
    }

    @Test
    void getAuthToken_shouldReturnViewerRole_forUser3() {
        // user3 has role 'viewer' in policy.csv
        when(mockWrappedAuth.getAuthToken(any())).thenReturn(List.of("user3"));

        List<String> roles = casbinAuth.getAuthToken(new Stash());

        assertEquals(1, roles.size());
        assertTrue(roles.contains("viewer"));
    }

    @Test
    void getAuthToken_shouldReturnEmptyList_whenWrappedAuthReturnsEmpty() {
        when(mockWrappedAuth.getAuthToken(any())).thenReturn(List.of());

        List<String> roles = casbinAuth.getAuthToken(new Stash());

        assertTrue(roles.isEmpty());
    }

    @Test
    void getAuthToken_shouldReturnEmptyList_whenWrappedAuthReturnsNull() {
        when(mockWrappedAuth.getAuthToken(any())).thenReturn(null);

        List<String> roles = casbinAuth.getAuthToken(new Stash());

        assertTrue(roles.isEmpty());
    }

    @Test
    void getAuthToken_shouldReturnEmptyList_forUnknownUser() {
        // unknown-user has no roles in policy.csv
        when(mockWrappedAuth.getAuthToken(any())).thenReturn(List.of("unknown-user"));

        List<String> roles = casbinAuth.getAuthToken(new Stash());

        assertTrue(roles.isEmpty());
    }

    // ==================== isAuthorized tests ====================

    @Test
    void isAuthorized_shouldReturnTrue_whenAuthorizedTokensIsNull() {
        Envelope envelope = new Envelope("id", new Stash(), Instant.now(), false, null);

        boolean result = casbinAuth.isAuthorized(List.of("admin"), envelope);

        assertTrue(result);
    }

    @Test
    void isAuthorized_shouldReturnTrue_whenAuthorizedTokensIsEmpty() {
        Envelope envelope = new Envelope("id", new Stash(), Instant.now(), false, List.of());

        boolean result = casbinAuth.isAuthorized(List.of("admin"), envelope);

        assertTrue(result);
    }

    @Test
    void isAuthorized_shouldReturnTrue_whenRoleMatchesAuthorizedTokens() {
        Envelope envelope = new Envelope("id", new Stash(), Instant.now(), false, List.of("admin", "editor"));

        boolean result = casbinAuth.isAuthorized(List.of("admin"), envelope);

        assertTrue(result);
    }

    @Test
    void isAuthorized_shouldReturnTrue_whenAnyRoleMatches() {
        Envelope envelope = new Envelope("id", new Stash(), Instant.now(), false, List.of("admin", "editor"));

        boolean result = casbinAuth.isAuthorized(List.of("viewer", "editor"), envelope);

        assertTrue(result);
    }

    @Test
    void isAuthorized_shouldReturnFalse_whenNoRoleMatches() {
        Envelope envelope = new Envelope("id", new Stash(), Instant.now(), false, List.of("admin", "editor"));

        boolean result = casbinAuth.isAuthorized(List.of("viewer"), envelope);

        assertFalse(result);
    }

    @Test
    void isAuthorized_shouldReturnFalse_whenCredentialsIsEmpty() {
        Envelope envelope = new Envelope("id", new Stash(), Instant.now(), false, List.of("admin"));

        boolean result = casbinAuth.isAuthorized(List.of(), envelope);

        assertFalse(result);
    }

    // ==================== Factory method tests ====================

    @Test
    void create_shouldCreateCasbinAuthFromPaths() {
        String modelPath = getClass().getClassLoader().getResource("model.conf").getPath();
        String policyPath = getClass().getClassLoader().getResource("policy.csv").getPath();

        CasbinAuth auth = CasbinAuth.create(modelPath, policyPath, mockWrappedAuth);

        assertNotNull(auth);
        assertNotNull(auth.getEnforcer());
    }

    @Test
    void getEnforcer_shouldReturnEnforcer() {
        assertNotNull(casbinAuth.getEnforcer());
        assertEquals(enforcer, casbinAuth.getEnforcer());
    }
}
