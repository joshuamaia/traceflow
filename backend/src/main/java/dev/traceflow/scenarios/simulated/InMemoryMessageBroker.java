package dev.traceflow.scenarios.simulated;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.Span;

/**
 * Minimal in-process message broker (topic with one partition) replacing Kafka/RabbitMQ for the async scenario.
 * Messages carry correlation headers exactly like a real broker would propagate them.
 */
public final class InMemoryMessageBroker {

    public static final String SERVICE = "message-broker";
    private static final String LOGGER = InMemoryMessageBroker.class.getName();

    /** A message with its propagated tracing headers. */
    public record Message(String id, String topic, long offset, String payload, String correlationId,
                          String brokerSpanId, Span brokerSpan) {
    }

    private final ScenarioContext ctx;
    private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
    private final AtomicLong offsets = new AtomicLong(4200);

    public InMemoryMessageBroker(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    /** Called by producers: accepts the message, logs the enqueue and returns its id. */
    public static String newMessageId() {
        return "msg-" + UUID.randomUUID();
    }

    public Message publish(Span producerSpan, String messageId, String topic, String payload) {
        Span span = ctx.startSpan(SERVICE, "broker", "enqueue " + topic, "PRODUCER", producerSpan)
                .messageId(messageId);
        long offset = offsets.incrementAndGet();
        ctx.advance(1);
        span.info(LOGGER, "Message " + messageId + " enqueued on topic " + topic + " partition=0 offset=" + offset,
                Map.of("messaging.destination", topic, "messaging.offset", offset));
        Message message = new Message(messageId, topic, offset, payload, ctx.correlationId(), span.spanId(), span);
        queue.add(message);
        return message;
    }

    /** Blocks until a message is available (used by consumer threads). */
    public Message take() throws InterruptedException {
        return queue.take();
    }

    /** Called when the consumer acknowledged the message. */
    public void acknowledge(Message message) {
        message.brokerSpan().end("INFO", LOGGER, "Message " + message.id() + " acknowledged by consumer", null, null,
                Map.of("messaging.destination", message.topic()));
    }
}
