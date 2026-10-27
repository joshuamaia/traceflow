package dev.traceflow.scenarios.catalog;

import java.util.List;
import java.util.Map;

import dev.traceflow.scenarios.engine.DemoScenario;
import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.ScenarioResult;
import dev.traceflow.scenarios.simulated.InMemoryMessageBroker;
import dev.traceflow.scenarios.simulated.NotificationService;
import dev.traceflow.scenarios.simulated.OrderService;
import org.springframework.stereotype.Component;

@Component
public class AsyncMessagingScenario implements DemoScenario {

    static final String TOPIC = "order.created";
    static final long DELIVERY_DELAY_MS = 140;

    @Override
    public String key() {
        return "async-messaging";
    }

    @Override
    public String name() {
        return "Async Messaging";
    }

    @Override
    public String description() {
        return "The order service publishes an order.created event to an in-memory broker and answers 202 right away. "
                + "The notification service consumes the message on its own thread, keeping the correlationId.";
    }

    @Override
    public List<String> flow() {
        return List.of("Order Service", "Message Broker", "Notification Service");
    }

    @Override
    public String expectedOutcome() {
        return "HTTP 202, message published and consumed asynchronously with measurable delivery latency";
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public ScenarioResult execute(ScenarioContext ctx) throws Exception {
        String orderId = "ORD-" + ScenarioContext.randomHex(3).toUpperCase();
        var broker = new InMemoryMessageBroker(ctx);
        var notifications = new NotificationService(ctx, broker);
        String logger = OrderService.class.getName();

        long start = ctx.clock().now().toEpochMilli();
        var order = ctx.startSpan(OrderService.SERVICE, "service", "POST /orders", "SERVER", null);
        order.info(logger, "Creating order " + orderId + " for customer C-1042", Map.of("orderId", orderId));
        ctx.advance(6);
        String messageId = InMemoryMessageBroker.newMessageId();
        var producer = ctx.startSpan(OrderService.SERVICE, "service", "publish " + TOPIC, "PRODUCER", order)
                .peer(InMemoryMessageBroker.SERVICE, "broker")
                .messageId(messageId);
        var publishedAt = ctx.clock().now();
        producer.info(logger, "Publishing " + TOPIC + " message " + messageId + " for order " + orderId,
                Map.of("messaging.destination", TOPIC));
        var message = broker.publish(producer, messageId, TOPIC, "{\"orderId\":\"" + orderId + "\"}");
        producer.end("INFO", logger, "Published " + TOPIC + " message " + message.id(), null, null,
                Map.of("messaging.destination", TOPIC));
        ctx.advance(2);
        long requestDuration = order.end("INFO", logger, "Order " + orderId + " accepted, notification will be sent "
                + "asynchronously", 202, null, null);

        var consumption = notifications.consumeOne(publishedAt, DELIVERY_DELAY_MS);

        ctx.detail("orderId", orderId);
        ctx.detail("messageId", message.id());
        ctx.detail("topic", TOPIC);
        ctx.detail("publishedAt", publishedAt.toString());
        ctx.detail("consumedAt", consumption.consumedAt().toString());
        ctx.detail("deliveryLatencyMs", consumption.deliveryLatencyMs());
        ctx.detail("consumerThread", consumption.consumerThread());
        ctx.detail("totalFlowMs", ctx.clock().now().toEpochMilli() - start);
        return new ScenarioResult(202, "Message " + message.id() + " consumed by notification-service after "
                + consumption.deliveryLatencyMs() + " ms", requestDuration);
    }
}
