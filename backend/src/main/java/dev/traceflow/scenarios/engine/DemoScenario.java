package dev.traceflow.scenarios.engine;

import java.util.List;

/** A deterministic, self-contained demo scenario producing structured logs. */
public interface DemoScenario {

    String key();

    String name();

    String description();

    /** Human readable flow, e.g. ["API Gateway", "Order Service", "Payment Service", "Database"]. */
    List<String> flow();

    String expectedOutcome();

    int order();

    ScenarioResult execute(ScenarioContext context) throws Exception;
}
