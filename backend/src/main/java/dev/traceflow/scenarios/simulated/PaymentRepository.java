package dev.traceflow.scenarios.simulated;

import java.util.Map;

import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.Span;

/** Persists payments in the (simulated) PostgreSQL database. */
public final class PaymentRepository {

    private static final String LOGGER = PaymentRepository.class.getName();

    private final ScenarioContext ctx;

    public PaymentRepository(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    public void save(Span parent, String paymentId) {
        Span db = ctx.startSpan(PaymentService.SERVICE, "service", "INSERT INTO payments", "CLIENT", parent)
                .peer("postgres-db", "database");
        ctx.advance(9);
        db.end("INFO", LOGGER, "Payment " + paymentId + " persisted (1 row)", null, null,
                Map.of("db.system", "postgresql", "db.operation", "INSERT", "db.table", "payments"));
    }
}
