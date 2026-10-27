package dev.traceflow.scenarios.simulated;

import java.time.Duration;
import java.util.Map;

import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.Span;

/** Payment service: authorizes payments with an acquirer and persists them. */
public final class PaymentService {

    public static final String SERVICE = "payment-service";
    private static final String CONTROLLER = PaymentService.class.getName();
    private static final String ACQUIRER = PaymentService.class.getName() + ".AcquirerClient";

    /** Authorization in progress; lets the caller give up while the server keeps working (timeouts). */
    public final class Authorization {
        private final Span span;
        private final long acquirerLatencyMs;

        private Authorization(Span span, long acquirerLatencyMs) {
            this.span = span;
            this.acquirerLatencyMs = acquirerLatencyMs;
        }

        /** Total server side processing time. */
        public long expectedDurationMs() {
            return 4 + acquirerLatencyMs + 12;
        }

        /** Normal completion: acquirer responds, payment is stored. */
        public SimulatedResponse complete(String paymentId) {
            ctx.advance(acquirerLatencyMs);
            span.info(ACQUIRER, "Acquirer approved authorization authCode=A" + paymentId.substring(4),
                    Map.of("acquirer", "demo-acquirer"));
            repository.save(span, paymentId);
            ctx.advance(2);
            span.end("INFO", CONTROLLER, "Payment " + paymentId + " authorized", 200, null, null);
            return SimulatedResponse.ok(paymentId);
        }

        /**
         * The caller already timed out: the acquirer answers late and writing the response fails because the client
         * closed the connection.
         */
        /** Logged while the acquirer has not answered yet. */
        public void reportSlowAcquirer(long thresholdMs) {
            span.warn(ACQUIRER, "Acquirer response time exceeded " + thresholdMs + " ms, still waiting",
                    Map.of("acquirer", "demo-acquirer", "thresholdMs", thresholdMs));
        }

        public void completeAfterCallerGaveUp(long callerTimeoutMs) {
            long elapsed = Duration.between(span.start(), ctx.clock().now()).toMillis();
            long acquirerDone = 4 + acquirerLatencyMs;
            if (acquirerDone > elapsed) {
                ctx.advance(acquirerDone - elapsed);
            }
            span.info(ACQUIRER, "Acquirer approved authorization after " + acquirerLatencyMs + " ms");
            ctx.advance(10);
            var abort = new ClientAbortException("Broken pipe - caller closed the connection before the response "
                    + "was written");
            long total = Duration.between(span.start(), ctx.clock().now()).toMillis();
            span.error(CONTROLLER, "Payment authorized but response could not be delivered: processing took "
                    + total + " ms, caller timeout is " + callerTimeoutMs + " ms", abort, null);
            ctx.advance(2);
            span.end("WARN", CONTROLLER, "Request aborted by client after " + (total + 2) + " ms", null, null, null);
        }
    }

    private final ScenarioContext ctx;
    private final PaymentRepository repository;

    public PaymentService(ScenarioContext ctx) {
        this.ctx = ctx;
        this.repository = new PaymentRepository(ctx);
    }

    public Authorization beginAuthorization(Span caller, String orderId, String amount, long acquirerLatencyMs) {
        Span span = ctx.startSpan(SERVICE, "service", "POST /payments/authorize", "SERVER", caller);
        span.info(CONTROLLER, "Authorizing payment for order " + orderId + " amount=" + amount + " currency=EUR",
                Map.of("orderId", orderId));
        ctx.advance(4);
        span.debug(ACQUIRER, "Calling acquirer demo-acquirer");
        return new Authorization(span, acquirerLatencyMs);
    }
}
