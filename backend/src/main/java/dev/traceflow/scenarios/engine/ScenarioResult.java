package dev.traceflow.scenarios.engine;

/**
 * @param httpStatus      status returned to the simulated client
 * @param outcome         one line summary
 * @param requestDuration duration of the (first) request in milliseconds as measured by the entry service
 */
public record ScenarioResult(int httpStatus, String outcome, long requestDuration) {
}
