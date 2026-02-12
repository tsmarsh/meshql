package com.meshql.examples.logistics;

import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Cucumber BDD test suite for the logistics example.
 *
 * Runs against a live MeshQL instance (docker-compose or Kubernetes).
 * Set API_BASE environment variable to override the default (http://localhost:3044).
 * Skipped in CI (requires running Docker Compose stack).
 *
 * Usage:
 *   docker-compose up -d
 *   mvn test -pl examples/logistics -Dtest=LogisticsBddTest
 */
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.meshql.examples.logistics")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, json:target/cucumber-reports/logistics-e2e.json")
public class LogisticsBddTest {
}
