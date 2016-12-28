package com.datastax.killrvideo.it.service;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(
        strict=false,
        plugin = {"progress", "html:/tmp/cucumber-report"},
        features = "src/test/resources")
public class ServicesTest {

}
