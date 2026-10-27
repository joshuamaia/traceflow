package dev.traceflow.scenarios.simulated;

import java.util.Map;
import java.util.function.Function;

import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.Span;

/** Edge gateway: receives the client request and routes it to an upstream service. */
public final class ApiGateway {

    public static final String SERVICE = "api-gateway";
    private static final String LOGGER = ApiGateway.class.getName();

    private final ScenarioContext ctx;

    public ApiGateway(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    public SimulatedResponse handle(String method, String path, String upstream, Function<Span, SimulatedResponse> route) {
        Span span = ctx.startSpan(SERVICE, "gateway", method + " " + path, "SERVER", null);
        span.info(LOGGER, "Incoming request " + method + " " + path + " from 203.0.113.24",
                Map.of("http.method", method, "http.path", path, "client.ip", "203.0.113.24"));
        ctx.advance(2);
        span.debug(LOGGER, "Route matched " + path + " -> " + upstream);
        SimulatedResponse response = route.apply(span);
        ctx.advance(1);
        if (response.status() >= 500) {
            span.end("WARN", LOGGER, "Upstream " + upstream + " failed, responding " + response.status(),
                    response.status(), null, null);
        } else {
            span.end("INFO", LOGGER, "Request completed " + method + " " + path + " status=" + response.status(),
                    response.status(), null, null);
        }
        return response;
    }
}
