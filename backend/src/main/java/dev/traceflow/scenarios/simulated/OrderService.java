package dev.traceflow.scenarios.simulated;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.Span;
import org.springframework.dao.DataAccessException;

/** Order service: validates orders, persists them and coordinates payment and notifications. */
public final class OrderService {

    public static final String SERVICE = "order-service";
    private static final String LOGGER = OrderService.class.getName();

    private final ScenarioContext ctx;
    private Runnable pendingWork;

    public OrderService(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    private Span begin(Span caller, String operation, String orderId) {
        Span span = ctx.startSpan(SERVICE, "service", operation, "SERVER", caller);
        span.info(LOGGER, "Creating order " + orderId + " for customer C-1042 with 3 items",
                Map.of("orderId", orderId, "customerId", "C-1042"));
        ctx.advance(5);
        span.debug(LOGGER, "Order " + orderId + " validated: total=149.90 EUR");
        return span;
    }

    /** Scenario 1: order with synchronous payment authorization. */
    public SimulatedResponse createOrderWithPayment(Span caller, String orderId, PaymentService payments) {
        Span span = begin(caller, "POST /orders", orderId);
        ctx.advance(1);
        span.info(LOGGER, "Requesting payment authorization from payment-service");
        String paymentId = "PAY-" + orderId.substring(4);
        SimulatedResponse payment = payments.beginAuthorization(span, orderId, "149.90", 18).complete(paymentId);
        ctx.advance(3);
        span.end("INFO", LOGGER, "Order " + orderId + " confirmed, payment " + payment.body(), 200, null,
                Map.of("orderId", orderId, "paymentId", paymentId));
        return SimulatedResponse.ok(orderId);
    }

    /** Scenario 2: payment call protected by a client side timeout. */
    public SimulatedResponse createOrderWithPaymentTimeout(Span caller, String orderId, PaymentService payments,
                                                           long timeoutMs, long acquirerLatencyMs) {
        Span span = begin(caller, "POST /orders", orderId);
        ctx.advance(1);
        span.info(LOGGER, "Requesting payment authorization from payment-service (timeout " + timeoutMs + " ms)");
        PaymentService.Authorization authorization = payments.beginAuthorization(span, orderId, "149.90",
                acquirerLatencyMs);
        try {
            awaitWithTimeout(authorization, timeoutMs);
            authorization.complete("PAY-" + orderId.substring(4));
            span.end("INFO", LOGGER, "Order confirmed", 200, null, null);
            return SimulatedResponse.ok(orderId);
        } catch (TimeoutException timeout) {
            span.error(LOGGER, "Payment authorization for order " + orderId + " timed out after " + timeoutMs + " ms",
                    timeout, Map.of("timeoutMs", timeoutMs));
            ctx.advance(1);
            span.end("WARN", LOGGER, "Order " + orderId + " failed: payment-service timeout", 504, null,
                    Map.of("orderId", orderId));
            // the payment service keeps working after the caller gave up
            pendingWork = () -> authorization.completeAfterCallerGaveUp(timeoutMs);
            return new SimulatedResponse(504, "Gateway Timeout");
        }
    }

    /** Runs work that continues in other services after the response was sent. */
    public void runPendingWork() {
        if (pendingWork != null) {
            Runnable work = pendingWork;
            pendingWork = null;
            work.run();
        }
    }

    private void awaitWithTimeout(PaymentService.Authorization authorization, long timeoutMs) throws TimeoutException {
        if (authorization.expectedDurationMs() > timeoutMs) {
            long slowThreshold = timeoutMs * 3 / 5;
            ctx.advance(slowThreshold);
            authorization.reportSlowAcquirer(slowThreshold);
            ctx.advance(timeoutMs - slowThreshold);
            throw new TimeoutException("Did not receive a response from payment-service within " + timeoutMs + " ms");
        }
    }

    /** Scenario 3: order persistence fails with a database exception. */
    public SimulatedResponse createOrderPersisting(Span caller, String orderId, OrderRepository repository) {
        Span span = begin(caller, "POST /orders", orderId);
        ctx.advance(1);
        try {
            repository.insert(span, orderId);
            span.end("INFO", LOGGER, "Order " + orderId + " created", 201, null, null);
            return new SimulatedResponse(201, orderId);
        } catch (DataAccessException e) {
            span.error(LOGGER, "Failed to persist order " + orderId + " in OrderRepository.insert", e,
                    Map.of("orderId", orderId, "operation.class", OrderRepository.class.getName(),
                            "operation.method", "insert"));
            ctx.advance(1);
            span.end("WARN", LOGGER, "Order " + orderId + " failed with HTTP 500", 500, null, null);
            return new SimulatedResponse(500, "Internal Server Error");
        }
    }
}
