package dev.traceflow.scenarios.catalog;

import java.util.List;

import dev.traceflow.scenarios.engine.DemoScenario;
import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.ScenarioResult;
import dev.traceflow.scenarios.simulated.ApiGateway;
import dev.traceflow.scenarios.simulated.OrderService;
import dev.traceflow.scenarios.simulated.PaymentService;
import org.springframework.stereotype.Component;

@Component
public class ServiceTimeoutScenario implements DemoScenario {

    static final long TIMEOUT_MS = 5000;
    static final long ACQUIRER_LATENCY_MS = 7300;

    @Override
    public String key() {
        return "service-timeout";
    }

    @Override
    public String name() {
        return "Service Timeout";
    }

    @Override
    public String description() {
        return "The payment service waits on a slow acquirer and exceeds the order service's 5 second timeout. "
                + "The order service gives up and the gateway answers 504 while the payment service keeps working.";
    }

    @Override
    public List<String> flow() {
        return List.of("API Gateway", "Order Service", "Payment Service");
    }

    @Override
    public String expectedOutcome() {
        return "HTTP 504 after a 5 s timeout; payment-service highlighted as the failing service";
    }

    @Override
    public int order() {
        return 2;
    }

    @Override
    public ScenarioResult execute(ScenarioContext ctx) {
        String orderId = "ORD-" + ScenarioContext.randomHex(3).toUpperCase();
        var gateway = new ApiGateway(ctx);
        var orders = new OrderService(ctx);
        var payments = new PaymentService(ctx);
        long start = ctx.clock().now().toEpochMilli();
        var response = gateway.handle("POST", "/api/orders", OrderService.SERVICE,
                span -> orders.createOrderWithPaymentTimeout(span, orderId, payments, TIMEOUT_MS, ACQUIRER_LATENCY_MS));
        long duration = ctx.clock().now().toEpochMilli() - start;
        orders.runPendingWork();
        ctx.detail("orderId", orderId);
        ctx.detail("timeoutMs", TIMEOUT_MS);
        ctx.detail("paymentProcessingMs", ctx.clock().now().toEpochMilli() - start);
        ctx.detail("failingService", PaymentService.SERVICE);
        return new ScenarioResult(response.status(), "payment-service exceeded the " + TIMEOUT_MS
                + " ms timeout, gateway answered HTTP " + response.status(), duration);
    }
}
