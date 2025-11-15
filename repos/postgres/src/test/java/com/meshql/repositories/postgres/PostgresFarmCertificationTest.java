package com.meshql.repositories.postgres;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Cucumber test suite for PostgreSQL Farm certification tests.
 * Uses the shared farm.feature from the cert module.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/e2e")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.meshql.cert.steps,com.meshql.repositories.postgres")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, json:target/cucumber-reports/postgres-farm-certification.json")
public class PostgresFarmCertificationTest {
}
