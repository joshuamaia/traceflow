package dev.traceflow.scenarios.catalog;

import java.util.List;

import dev.traceflow.scenarios.engine.DemoScenario;
import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.ScenarioResult;
import dev.traceflow.scenarios.simulated.ApiGateway;
import dev.traceflow.scenarios.simulated.OrderRepository;
import dev.traceflow.scenarios.simulated.OrderService;
import org.springframework.stereotype.Component;

@Component
public class DatabaseExceptionScenario implements DemoScenario {

    @Override
    public String key() {
        return "database-exception";
    }

    @Override
    public String name() {
        return "Database Exception";
    }

    @Override
    public String description() {
        return "Persisting the order violates a unique constraint. The JDBC SQLException is translated into a "
                + "DataIntegrityViolationException and the request fails with HTTP 500.";
    }

    @Override
    public List<String> flow() {
        return List.of("API Gateway", "Order Service", "Database");
    }

    @Override
    public String expectedOutcome() {
        return "HTTP 500 with a Java stack trace pointing to OrderRepository.insert";
    }

    @Override
    public int order() {
        return 3;
    }

    @Override
    public ScenarioResult execute(ScenarioContext ctx) {
        String orderId = "ORD-" + ScenarioContext.randomHex(3).toUpperCase();
        var gateway = new ApiGateway(ctx);
        var orders = new OrderService(ctx);
        var repository = new OrderRepository(ctx, true);
        long start = ctx.clock().now().toEpochMilli();
        var response = gateway.handle("POST", "/api/orders", OrderService.SERVICE,
                span -> orders.createOrderPersisting(span, orderId, repository));
        long duration = ctx.clock().now().toEpochMilli() - start;
        ctx.detail("orderId", orderId);
        ctx.detail("exception", "org.springframework.dao.DataIntegrityViolationException");
        ctx.detail("rootCause", "java.sql.SQLException (SQLState 23505)");
        ctx.detail("failingOperation", OrderRepository.class.getName() + ".insert");
        return new ScenarioResult(response.status(), "INSERT INTO orders failed with a constraint violation (HTTP "
                + response.status() + ")", duration);
    }
}
