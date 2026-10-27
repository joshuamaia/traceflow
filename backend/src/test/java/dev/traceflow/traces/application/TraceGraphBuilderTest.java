package dev.traceflow.traces.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.traceflow.events.domain.LogEvent;
import dev.traceflow.traces.application.TraceDtos.EdgeDto;
import dev.traceflow.traces.domain.DurationKind;
import dev.traceflow.traces.domain.Trace;
import dev.traceflow.traces.domain.TraceStatus;
import org.junit.jupiter.api.Test;

class TraceGraphBuilderTest {

    private final TraceGraphBuilder builder = new TraceGraphBuilder(json -> json == null ? Map.of() : Map.of("serviceType", json));
    private final List<LogEvent> events = new ArrayList<>();
    private final Instant t0 = Instant.parse("2026-10-27T10:00:00Z");

    private LogEvent add(String service, String spanId, String parent, long offsetMs, String message) {
        var e = new LogEvent();
        e.setId(UUID.randomUUID());
        e.setSeq(events.size() + 1);
        e.setService(service);
        e.setSpanId(spanId);
        e.setParentSpanId(parent);
        e.setTimestamp(t0.plusMillis(offsetMs));
        e.setLevel("INFO");
        e.setMessage(message);
        events.add(e);
        return e;
    }

    private Trace trace() {
        var t = new Trace();
        t.setId(UUID.randomUUID());
        t.setAnalysisId(UUID.randomUUID());
        t.setSourceKind("IMPORT");
        t.setDurationKind(DurationKind.REPORTED);
        t.setStatus(TraceStatus.OK);
        t.setServices("");
        return t;
    }

    @Test
    void buildsConfirmedEdgesAndWaterfallFromSpans() {
        add("gateway", "g", null, 0, "in").setAttributes("gateway");
        add("orders", "o", "g", 2, "create order");
        add("orders", "o", "g", 40, "done").setDurationMs(38L);
        var db = add("orders", "db", "o", 30, "insert");
        db.setPeerService("postgres-db");
        db.setPeerType("database");
        db.setSpanKind("CLIENT");
        db.setDurationMs(9L);
        add("gateway", "g", null, 45, "out").setDurationMs(45L);

        var detail = builder.build(trace(), events, events.size());

        assertThat(detail.edges()).extracting(EdgeDto::id).containsExactlyInAnyOrder("gateway->orders", "orders->postgres-db");
        assertThat(detail.edges()).allMatch(e -> e.relation().equals("CONFIRMED") && e.mode().equals("SYNC"));
        assertThat(detail.nodes()).anySatisfy(n -> {
            assertThat(n.name()).isEqualTo("postgres-db");
            assertThat(n.virtual()).isTrue();
            assertThat(n.type()).isEqualTo("DATABASE");
        });
        assertThat(detail.nodes()).anySatisfy(n -> assertThat(n.type()).isEqualTo("GATEWAY"));
        assertThat(detail.timingBasis()).isEqualTo("SPANS");
        var dbSpan = detail.spans().stream().filter(s -> s.spanId().equals("db")).findFirst().orElseThrow();
        assertThat(dbSpan.startOffsetMs()).as("start derived from completion - duration").isEqualTo(21);
        assertThat(dbSpan.durationKind()).isEqualTo("REPORTED");
        assertThat(dbSpan.depth()).isEqualTo(2);
        assertThat(detail.relationships().timelineOnly()).isFalse();
        assertThat(detail.errorOrigin()).isNull();
    }

    @Test
    void marksAsyncEdgesAndMeasuresDeliveryLatency() {
        var producer = add("orders", "p", null, 0, "publish");
        producer.setSpanKind("PRODUCER");
        producer.setMessageId("m-1");
        var consumer = add("notifications", "c", "p", 140, "received");
        consumer.setSpanKind("CONSUMER");
        consumer.setMessageId("m-1");

        var edge = builder.build(trace(), events, events.size()).edges().getFirst();

        assertThat(edge.mode()).isEqualTo("ASYNC");
        assertThat(edge.asyncLatencyMs()).isEqualTo(140);
    }

    @Test
    void infersRelationsOnlyFromExplicitMessagesAndLabelsThem() {
        add("order-service", null, null, 0, "Calling payment-service for order 7");
        add("payment-service", null, null, 30, "charging card");

        var detail = builder.build(trace(), events, events.size());

        assertThat(detail.edges()).singleElement().satisfies(e -> {
            assertThat(e.relation()).isEqualTo("INFERRED");
            assertThat(e.evidence()).contains("Calling payment-service");
        });
        assertThat(detail.timingBasis()).isEqualTo("SERVICE_WINDOWS");
        assertThat(detail.notes()).anyMatch(n -> n.contains("INFERRED"));
    }

    @Test
    void showsTimelineOnlyWithoutEvidence() {
        add("svc-a", null, null, 0, "started");
        add("svc-b", null, null, 10, "something else").setLevel("ERROR");
        events.get(1).setError(true);

        var detail = builder.build(trace(), events, events.size());

        assertThat(detail.edges()).isEmpty();
        assertThat(detail.relationships().timelineOnly()).isTrue();
        assertThat(detail.relationships().unlinkedServices()).containsExactly("svc-a", "svc-b");
        assertThat(detail.timeline()).hasSize(2);
        assertThat(detail.errorOrigin().service()).isEqualTo("svc-b");
        assertThat(detail.errorOrigin().reason()).contains("no span hierarchy");
    }

    @Test
    void pointsErrorOriginToTheDeepestFailingSpan() {
        add("gateway", "g", null, 0, "in");
        var order = add("orders", "o", "g", 1, "timeout");
        order.setError(true);
        var payment = add("payments", "p", "o", 2, "client aborted");
        payment.setError(true);

        var detail = builder.build(trace(), events, events.size());

        assertThat(detail.errorOrigin().service()).isEqualTo("payments");
        assertThat(detail.errorOrigin().eventId()).isEqualTo(payment.getId());
    }

    @Test
    void reportsSpansWhoseParentIsMissing() {
        add("orders", "o", "missing-parent", 0, "x");

        var detail = builder.build(trace(), events, events.size());

        assertThat(detail.relationships().spansWithMissingParent()).isEqualTo(1);
        assertThat(detail.spans().getFirst().parentMissing()).isTrue();
    }
}
