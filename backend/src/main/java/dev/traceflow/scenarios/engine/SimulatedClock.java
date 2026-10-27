package dev.traceflow.scenarios.engine;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Virtual clock used by demo scenarios. Simulated latencies (e.g. a 5 second timeout) advance the clock instead of
 * sleeping, which keeps executions fast and their timings deterministic while timestamps stay realistic.
 */
public final class SimulatedClock {

    private final AtomicLong epochMillis;

    public SimulatedClock(Instant start) {
        this.epochMillis = new AtomicLong(start.truncatedTo(ChronoUnit.MILLIS).toEpochMilli());
    }

    public Instant now() {
        return Instant.ofEpochMilli(epochMillis.get());
    }

    public void advance(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("Time cannot go backwards");
        }
        epochMillis.addAndGet(millis);
    }
}
