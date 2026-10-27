package dev.traceflow.scenarios.application;

import dev.traceflow.config.TraceflowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Executes each demo scenario once on the first start so the dashboard has data immediately. */
@Component
public class DemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final ScenarioService scenarios;
    private final TraceflowProperties properties;

    public DemoSeeder(ScenarioService scenarios, TraceflowProperties properties) {
        this.scenarios = scenarios;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!properties.demo().seedOnStartup()) {
            return;
        }
        try {
            scenarios.seedIfEmpty();
        } catch (RuntimeException e) {
            log.warn("Demo seeding failed: {}", e.getMessage());
        }
    }
}
