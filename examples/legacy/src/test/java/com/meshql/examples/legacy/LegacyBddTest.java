package com.meshql.examples.legacy;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Cucumber BDD test suite for the legacy anti-corruption layer example.
 *
 * Runs against a live MeshQL instance (docker-compose).
 * Set API_BASE environment variable to override the default (http://localhost:4066).
 *
 * Usage:
 *   docker-compose up -d
 *   mvn test -pl examples/legacy -Dtest=LegacyBddTest
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.meshql.examples.legacy")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, json:target/cucumber-reports/legacy-e2e.json")
public class LegacyBddTest {
}
