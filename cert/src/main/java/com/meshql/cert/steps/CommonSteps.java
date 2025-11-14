package com.meshql.cert.steps;

import com.meshql.cert.IntegrationWorld;
import io.cucumber.java.en.Given;

public class CommonSteps {
    private final IntegrationWorld world;

    public CommonSteps(IntegrationWorld world) {
        this.world = world;
    }

    @Given("I capture the current timestamp as {string}")
    public void captureTimestamp(String label) {
        world.timestamps.put(label, System.currentTimeMillis());
    }

    @Given("I wait {int} milliseconds")
    public void waitMilliseconds(int ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
