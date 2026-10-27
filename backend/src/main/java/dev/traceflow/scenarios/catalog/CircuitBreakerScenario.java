package dev.traceflow.scenarios.catalog;

import java.util.ArrayList;
import java.util.List;

import dev.traceflow.scenarios.engine.DemoScenario;
import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.ScenarioResult;
import dev.traceflow.scenarios.simulated.ExternalPaymentApiClient;
import dev.traceflow.scenarios.simulated.ResilientCheckoutService;
import org.springframework.stereotype.Component;

@Component
public class CircuitBreakerScenario implements DemoScenario {

    static final int FOLLOW_UP_REQUESTS = 2;

    @Override
    public String key() {
        return "circuit-breaker";
    }

    @Override
    public String name() {
        return "Circuit Breaker & Retries";
    }

    @Override
    public String description() {
        return "The external payment provider is unavailable. Resilience4j retries the call three times; the "
                + "circuit breaker then opens and the client's follow-up checkout attempts are rejected immediately.";
    }

    @Override
    public List<String> flow() {
        return List.of("Order Service", "External Payment API");
    }

    @Override
    public String expectedOutcome() {
        return "3 failed attempts, circuit breaker CLOSED -> OPEN, follow-up calls rejected (HTTP 503)";
    }

    @Override
    public int order() {
        return 4;
    }

    @Override
    public ScenarioResult execute(ScenarioContext ctx) {
        String orderId = "ORD-" + ScenarioContext.randomHex(3).toUpperCase();
        var client = new ExternalPaymentApiClient(ctx);
        var checkout = new ResilientCheckoutService(ctx, client);
        long start = ctx.clock().now().toEpochMilli();
        var first = checkout.checkout(orderId, "POST /orders/" + orderId + "/checkout");
        long firstDuration = ctx.clock().now().toEpochMilli() - start;
        List<Integer> followUps = new ArrayList<>();
        for (int i = 1; i <= FOLLOW_UP_REQUESTS; i++) {
            ctx.advance(400);
            followUps.add(checkout.checkout(orderId, "POST /orders/" + orderId + "/checkout (client retry #" + i + ")")
                    .status());
        }
        ctx.detail("orderId", orderId);
        ctx.detail("attempts", client.attempts());
        ctx.detail("circuitBreakerState", checkout.state());
        ctx.detail("stateTransitions", checkout.transitions());
        ctx.detail("rejectedCalls", FOLLOW_UP_REQUESTS);
        ctx.detail("followUpStatuses", followUps);
        return new ScenarioResult(first.status(), client.attempts() + " attempts failed, circuit breaker "
                + checkout.state() + ", " + FOLLOW_UP_REQUESTS + " follow-up calls rejected", firstDuration);
    }
}
