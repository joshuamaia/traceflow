package dev.traceflow.scenarios.simulated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.Span;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Order checkout protected by Resilience4j: a {@link Retry} (3 attempts, exponential backoff) wrapping a
 * {@link CircuitBreaker} (count based window of 3 calls, opens at 50% failure rate).
 * <p>
 * Backoff waits advance the virtual clock; the real Resilience4j instances decide retries and state transitions.
 */
public final class ResilientCheckoutService {

    public static final String BREAKER = "externalPayment";
    private static final String LOGGER = ResilientCheckoutService.class.getName();
    private static final long BASE_BACKOFF_MS = 200;

    private final ScenarioContext ctx;
    private final ExternalPaymentApiClient client;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final List<String> transitions = new ArrayList<>();
    private Span currentSpan;

    public ResilientCheckoutService(ScenarioContext ctx, ExternalPaymentApiClient client) {
        this.ctx = ctx;
        this.client = client;
        this.circuitBreaker = CircuitBreaker.of(BREAKER, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(3)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordExceptions(PaymentProviderUnavailableException.class)
                .build());
        this.retry = Retry.of(BREAKER, RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.of(Duration.ofMillis(1)))
                .retryExceptions(PaymentProviderUnavailableException.class)
                .ignoreExceptions(CallNotPermittedException.class)
                .build());

        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            var transition = event.getStateTransition();
            transitions.add(transition.getFromState() + " -> " + transition.getToState());
            currentSpan.warn(LOGGER, "CircuitBreaker '" + BREAKER + "' changed state from " + transition.getFromState()
                            + " to " + transition.getToState() + " (failure rate "
                            + circuitBreaker.getMetrics().getFailureRate() + "% over "
                            + circuitBreaker.getMetrics().getNumberOfBufferedCalls() + " calls)",
                    Map.of("circuitBreaker", BREAKER, "circuitBreaker.state", transition.getToState().name(),
                            "circuitBreaker.previousState", transition.getFromState().name()));
        });
        retry.getEventPublisher().onRetry(event -> {
            long backoff = BASE_BACKOFF_MS * (1L << (event.getNumberOfRetryAttempts() - 1));
            currentSpan.warn(LOGGER, "Retry '" + BREAKER + "' attempt " + (event.getNumberOfRetryAttempts() + 1) + "/3 in "
                            + backoff + " ms after: " + event.getLastThrowable().getMessage(),
                    Map.of("retry.attempt", event.getNumberOfRetryAttempts() + 1, "retry.backoffMs", backoff));
            ctx.advance(backoff);
        });
    }

    /** Handles one checkout request (a SERVER span of order-service). */
    public SimulatedResponse checkout(String orderId, String operation) {
        Span span = ctx.startSpan(OrderService.SERVICE, "service", operation, "SERVER", null);
        currentSpan = span;
        span.info(LOGGER, "Checkout for order " + orderId + ": charging 149.90 EUR via external-payment-api",
                Map.of("orderId", orderId, "circuitBreaker.state", circuitBreaker.getState().name()));
        ctx.advance(2);
        Supplier<String> guarded = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> client.charge(span, orderId)));
        try {
            String charge = guarded.get();
            span.end("INFO", LOGGER, "Order " + orderId + " paid: " + charge, 200, null, null);
            return SimulatedResponse.ok(charge);
        } catch (CallNotPermittedException rejected) {
            span.error(LOGGER, "Call rejected: CircuitBreaker '" + BREAKER + "' is OPEN, external-payment-api not called",
                    rejected, Map.of("circuitBreaker", BREAKER, "circuitBreaker.state", circuitBreaker.getState().name()));
            ctx.advance(1);
            span.end("WARN", LOGGER, "Checkout of order " + orderId + " failed fast with HTTP 503", 503, null,
                    Map.of("circuitBreaker.state", circuitBreaker.getState().name()));
            return new SimulatedResponse(503, "Service Unavailable");
        } catch (PaymentProviderUnavailableException failed) {
            span.error(LOGGER, "Payment for order " + orderId + " failed after " + client.attempts() + " attempts", failed,
                    Map.of("retry.attempts", client.attempts(), "circuitBreaker.state", circuitBreaker.getState().name()));
            ctx.advance(1);
            span.end("WARN", LOGGER, "Checkout of order " + orderId + " failed with HTTP 503", 503, null,
                    Map.of("circuitBreaker.state", circuitBreaker.getState().name()));
            return new SimulatedResponse(503, "Service Unavailable");
        }
    }

    public String state() {
        return circuitBreaker.getState().name();
    }

    public List<String> transitions() {
        return List.copyOf(transitions);
    }
}
