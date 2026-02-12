package com.meshql.examples.logistics;

import io.cucumber.java.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cucumber hooks for logistics BDD tests.
 * Sets up the API base URL from environment variable.
 */
public class LogisticsHooks {
    private static final Logger logger = LoggerFactory.getLogger(LogisticsHooks.class);

    @Before
    public void setUp() {
        if (LogisticsWorld.apiBase == null) {
            LogisticsWorld.apiBase = System.getenv("API_BASE");
            if (LogisticsWorld.apiBase == null || LogisticsWorld.apiBase.isEmpty()) {
                LogisticsWorld.apiBase = "http://localhost:3044";
            }
            logger.info("Logistics API base URL: {}", LogisticsWorld.apiBase);
        }
    }
}
