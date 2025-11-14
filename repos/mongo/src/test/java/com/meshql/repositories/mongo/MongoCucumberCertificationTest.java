package com.meshql.repositories.mongo;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Cucumber-based certification test suite for MongoDB plugin.
 *
 * Uses the same feature files as meshobj (via symlink in cert module)
 * to ensure consistent behavior across TypeScript and Java implementations.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/integration")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.meshql.cert.steps,com.meshql.repositories.mongo")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, json:target/cucumber-reports/mongo-certification.json")
public class MongoCucumberCertificationTest {
}
