package dev.traceflow.scenarios.simulated;

import java.sql.SQLException;
import java.util.Map;

import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.Span;
import org.springframework.dao.DataIntegrityViolationException;

/** Persists orders. Configured to fail with a constraint violation in the "Database Exception" scenario. */
public final class OrderRepository {

    public static final String LOGGER = OrderRepository.class.getName();

    private final ScenarioContext ctx;
    private final boolean failOnInsert;

    public OrderRepository(ScenarioContext ctx, boolean failOnInsert) {
        this.ctx = ctx;
        this.failOnInsert = failOnInsert;
    }

    public void insert(Span parent, String orderId) {
        Span db = ctx.startSpan(OrderService.SERVICE, "service", "INSERT INTO orders", "CLIENT", parent)
                .peer("postgres-db", "database");
        ctx.advance(11);
        Map<String, Object> dbAttributes = Map.of("db.system", "postgresql", "db.operation", "INSERT",
                "db.table", "orders");
        if (failOnInsert) {
            var exception = executeInsert(orderId);
            db.end("ERROR", LOGGER, "INSERT INTO orders failed: "
                            + exception.getMostSpecificCause().getMessage().lines().findFirst().orElse(""), null,
                    null, Map.of("db.system", "postgresql", "db.operation", "INSERT", "db.table", "orders",
                            "db.sqlState", "23505", "error.kind", "java.sql.SQLException"));
            throw exception;
        }
        db.end("INFO", LOGGER, "Order " + orderId + " persisted (1 row)", null, null, dbAttributes);
    }

    /** Simulates the JDBC driver rejecting the statement and Spring translating the SQLException. */
    private DataIntegrityViolationException executeInsert(String orderId) {
        try {
            throw new SQLException("ERROR: duplicate key value violates unique constraint \"orders_order_number_key\"\n"
                    + "  Detail: Key (order_number)=(" + orderId + ") already exists.", "23505");
        } catch (SQLException sql) {
            return new DataIntegrityViolationException(
                    "could not execute statement [INSERT INTO orders (order_number, customer_id, total) VALUES (?, ?, ?)]",
                    sql);
        }
    }
}
