package dev.traceflow.scenarios.simulated;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.Span;

/** Consumes order events from the broker on its own thread and sends notifications. */
public final class NotificationService {

    public static final String SERVICE = "notification-service";
    private static final String LOGGER = NotificationService.class.getName();

    /** What the consumer observed, reported in the scenario details. */
    public record Consumption(String messageId, Instant consumedAt, long deliveryLatencyMs, String consumerThread) {
    }

    private final ScenarioContext ctx;
    private final InMemoryMessageBroker broker;

    public NotificationService(ScenarioContext ctx, InMemoryMessageBroker broker) {
        this.ctx = ctx;
        this.broker = broker;
    }

    /**
     * Starts a consumer thread, processes exactly one message and waits for it to finish.
     *
     * @param deliveryDelayMs simulated broker delivery delay
     */
    public Consumption consumeOne(Instant publishedAt, long deliveryDelayMs) throws InterruptedException {
        AtomicReference<Consumption> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread consumer = Thread.ofPlatform().name("notification-consumer-1").unstarted(() -> {
            try {
                InMemoryMessageBroker.Message message = broker.take();
                ctx.advance(deliveryDelayMs);
                Span span = ctx.startSpan(SERVICE, "service", "consume " + message.topic(), "CONSUMER",
                                message.brokerSpan())
                        .thread(Thread.currentThread().getName())
                        .messageId(message.id());
                long latency = Duration.between(publishedAt, ctx.clock().now()).toMillis();
                span.info(LOGGER, "Received " + message.topic() + " message " + message.id() + " (delivery latency "
                        + latency + " ms)", Map.of("messaging.destination", message.topic(),
                        "messaging.offset", message.offset()));
                ctx.advance(28);
                span.info(LOGGER, "Order confirmation e-mail queued for customer C-1042");
                ctx.advance(6);
                broker.acknowledge(message);
                ctx.advance(1);
                span.end("INFO", LOGGER, "Message " + message.id() + " processed", null, null, null);
                result.set(new Consumption(message.id(), span.start(), latency, Thread.currentThread().getName()));
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        consumer.start();
        consumer.join(10_000);
        if (failure.get() != null) {
            throw new IllegalStateException("Consumer failed", failure.get());
        }
        if (result.get() == null) {
            consumer.interrupt();
            throw new IllegalStateException("Consumer did not process the message in time");
        }
        return result.get();
    }
}
