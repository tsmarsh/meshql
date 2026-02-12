package com.meshql.examples.legacy;

import io.cucumber.java.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cucumber hooks for legacy BDD tests.
 * Sets up the API base URL from environment variable.
 */
public class LegacyHooks {
    private static final Logger logger = LoggerFactory.getLogger(LegacyHooks.class);

    @Before
    public void setUp() {
        if (LegacyWorld.apiBase == null) {
            LegacyWorld.apiBase = System.getenv("API_BASE");
            if (LegacyWorld.apiBase == null || LegacyWorld.apiBase.isEmpty()) {
                LegacyWorld.apiBase = "http://localhost:4066";
            }
            logger.info("Legacy API base URL: {}", LegacyWorld.apiBase);
        }
    }
}
