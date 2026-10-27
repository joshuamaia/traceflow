package dev.traceflow.scenarios.engine;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Execution context of one scenario run: fresh identifiers, the virtual clock and the structured log sink.
 * Behaviour is deterministic; only identifiers and timestamps change between runs.
 */
public final class ScenarioContext {

    private final SimulatedClock clock;
    private final ScenarioLog log;
    private final String correlationId;
    private final String traceId;
    private final Map<String, Object> details = new LinkedHashMap<>();

    public ScenarioContext(SimulatedClock clock, ScenarioLog log) {
        this.clock = clock;
        this.log = log;
        this.correlationId = "corr-" + randomHex(6);
        this.traceId = randomHex(16);
    }

    public Span startSpan(String service, String serviceType, String operation, String kind, Span parent) {
        return new Span(this, randomHex(8), parent == null ? null : parent.spanId(), service, serviceType, operation,
                kind);
    }

    public void advance(long millis) {
        clock.advance(millis);
    }

    public SimulatedClock clock() {
        return clock;
    }

    public ScenarioLog log() {
        return log;
    }

    public String correlationId() {
        return correlationId;
    }

    public String traceId() {
        return traceId;
    }

    /** Technical details returned with the run (attempt counts, message ids, ...). */
    public void detail(String key, Object value) {
        details.put(key, value);
    }

    public Map<String, Object> details() {
        return details;
    }

    public static String randomHex(int bytes) {
        byte[] data = new byte[bytes];
        ThreadLocalRandom.current().nextBytes(data);
        return HexFormat.of().formatHex(data);
    }
}
