package com.meshql.repositories.mongo;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Cucumber-based farm certification test suite for MongoDB plugin.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/e2e")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.meshql.cert.steps,com.meshql.repositories.mongo")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, json:target/cucumber-reports/mongo-farm-certification.json")
public class MongoFarmCertificationTest {
}
