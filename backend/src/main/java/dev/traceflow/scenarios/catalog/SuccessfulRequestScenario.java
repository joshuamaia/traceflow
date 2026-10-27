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
public class SuccessfulRequestScenario implements DemoScenario {

    @Override
    public String key() {
        return "successful-request";
    }

    @Override
    public String name() {
        return "Successful Request";
    }

    @Override
    public String description() {
        return "A client creates an order. The gateway routes it to the order service, which authorizes the payment; "
                + "the payment service persists it in the database. Every service completes successfully.";
    }

    @Override
    public List<String> flow() {
        return List.of("API Gateway", "Order Service", "Payment Service", "Database");
    }

    @Override
    public String expectedOutcome() {
        return "HTTP 200, all spans successful, complete timeline, no errors";
    }

    @Override
    public int order() {
        return 1;
    }

    @Override
    public ScenarioResult execute(ScenarioContext ctx) {
        String orderId = "ORD-" + ScenarioContext.randomHex(3).toUpperCase();
        var gateway = new ApiGateway(ctx);
        var orders = new OrderService(ctx);
        var payments = new PaymentService(ctx);
        long start = ctx.clock().now().toEpochMilli();
        var response = gateway.handle("POST", "/api/orders", OrderService.SERVICE,
                span -> orders.createOrderWithPayment(span, orderId, payments));
        long duration = ctx.clock().now().toEpochMilli() - start;
        ctx.detail("orderId", orderId);
        ctx.detail("services", flow());
        return new ScenarioResult(response.status(), "Order " + orderId + " created and paid (HTTP "
                + response.status() + ")", duration);
    }
}
