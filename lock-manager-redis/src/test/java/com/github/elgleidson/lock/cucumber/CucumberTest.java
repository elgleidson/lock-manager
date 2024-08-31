package com.github.elgleidson.lock.cucumber;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectPackages("com.github.elgleidson.lock.cucumber")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.github.elgleidson.lock.cucumber")
public class CucumberTest {

}