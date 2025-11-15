package com.meshql.repos.sqlite;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * SQLite farm certification test suite.
 * Runs end-to-end tests validating SQLite backend with multi-node GraphQL server.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/e2e")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.meshql.cert.steps,com.meshql.repos.sqlite")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, json:target/cucumber-reports/sqlite-farm-certification.json")
public class SQLiteFarmCertificationTest {
}
