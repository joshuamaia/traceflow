package dev.traceflow.scenarios.simulated;

/** HTTP response returned by a simulated service. */
public record SimulatedResponse(int status, String body) {

    public static SimulatedResponse ok(String body) {
        return new SimulatedResponse(200, body);
    }
}
