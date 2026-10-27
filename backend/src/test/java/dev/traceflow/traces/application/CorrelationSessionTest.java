package dev.traceflow.traces.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import dev.traceflow.parsing.ParsedEvent;
import dev.traceflow.traces.domain.DurationKind;
import dev.traceflow.traces.domain.Trace;
import dev.traceflow.traces.domain.TraceStatus;
import org.junit.jupiter.api.Test;

class CorrelationSessionTest {

    private long seq;

    private ParsedEvent event(String service, String correlationId, String traceId, String ts) {
        var e = new ParsedEvent("TEST", (int) ++seq, "raw");
        e.setService(service);
        e.setCorrelationId(correlationId);
        e.setTraceId(traceId);
        e.setLevel("INFO");
        e.setTimestamp(ts == null ? null : Instant.parse(ts));
        return e;
    }

    @Test
    void groupsEventsByCorrelationIdAndIgnoresUncorrelatedEvents() {
        var session = new CorrelationSession();
        var a = session.assign(event("gateway", "c1", null, "2026-10-27T10:00:00Z"), 1);
        var b = session.assign(event("orders", "c1", null, "2026-10-27T10:00:00.250Z"), 2);
        var c = session.assign(event("orders", "c2", null, "2026-10-27T10:00:01Z"), 3);
        var none = session.assign(event("orders", null, null, "2026-10-27T10:00:02Z"), 4);

        assertThat(a.traceRef()).isEqualTo(b.traceRef()).isNotEqualTo(c.traceRef());
        assertThat(none).isNull();
        assertThat(session.traceCount()).isEqualTo(2);
    }

    @Test
    void linksTraceIdOnlyEventsThroughAnEventCarryingBothIds() {
        var session = new CorrelationSession();
        var first = session.assign(event("payments", null, "t-9", "2026-10-27T10:00:00Z"), 1);
        var bridge = session.assign(event("orders", "c-9", "t-9", "2026-10-27T10:00:01Z"), 2);
        var later = session.assign(event("orders", "c-9", null, "2026-10-27T10:00:02Z"), 3);

        assertThat(first.traceRef()).isEqualTo(bridge.traceRef()).isEqualTo(later.traceRef());
        Trace trace = session.buildTraces(UUID.randomUUID(), "IMPORT").getFirst();
        assertThat(trace.getCorrelationId()).isEqualTo("c-9");
        assertThat(trace.getTraceId()).isEqualTo("t-9");
        assertThat(trace.getEventCount()).isEqualTo(3);
        assertThat(trace.serviceList()).containsExactly("payments", "orders");
    }

    @Test
    void mergesTwoExistingTracesAndRecordsTheMove() {
        var session = new CorrelationSession();
        var byCorrelation = session.assign(event("a", "c-1", null, null), 1);
        session.assign(event("a", "c-1", null, null), 2);
        var byTrace = session.assign(event("b", null, "t-1", null), 3);
        var bridge = session.assign(event("b", "c-1", "t-1", null), 4);

        assertThat(bridge.traceRef()).isEqualTo(byCorrelation.traceRef());
        assertThat(session.traceCount()).isEqualTo(1);
        assertThat(session.merges()).containsExactly(new CorrelationSession.Merge(byTrace.traceRef(), byCorrelation.traceRef()));
    }

    @Test
    void usesReportedRootDurationAndRootSpanStatus() {
        var session = new CorrelationSession();
        var root = event("gateway", "c1", "t1", "2026-10-27T10:00:00Z");
        root.setSpanId("root");
        var child = event("orders", "c1", "t1", "2026-10-27T10:00:00.100Z");
        child.setSpanId("child");
        child.setParentSpanId("root");
        child.setLevel("ERROR");
        var rootEnd = event("gateway", "c1", "t1", "2026-10-27T10:00:00.300Z");
        rootEnd.setSpanId("root");
        rootEnd.setDurationMs(300L);
        rootEnd.setHttpStatus(500);
        session.assign(root, 1);
        session.assign(child, 2);
        session.assign(rootEnd, 3);

        Trace trace = session.buildTraces(UUID.randomUUID(), "DEMO").getFirst();
        assertThat(trace.getDurationKind()).isEqualTo(DurationKind.REPORTED);
        assertThat(trace.getDurationMs()).isEqualTo(300);
        assertThat(trace.getRootService()).isEqualTo("gateway");
        assertThat(trace.getHttpStatus()).isEqualTo(500);
        assertThat(trace.getStatus()).isEqualTo(TraceStatus.ERROR);
        assertThat(trace.getErrorCount()).isEqualTo(1);
    }

    @Test
    void fallsBackToObservedOrUnknownDurations() {
        var session = new CorrelationSession();
        session.assign(event("a", "obs", null, "2026-10-27T10:00:00Z"), 1);
        session.assign(event("a", "obs", null, "2026-10-27T10:00:02Z"), 2);
        session.assign(event("a", "single", null, "2026-10-27T10:00:00Z"), 3);
        session.assign(event("a", "nots", null, null), 4);

        var byCorrelation = session.buildTraces(UUID.randomUUID(), "IMPORT").stream()
                .collect(java.util.stream.Collectors.toMap(Trace::getCorrelationId, t -> t));
        assertThat(byCorrelation.get("obs").getDurationKind()).isEqualTo(DurationKind.OBSERVED);
        assertThat(byCorrelation.get("obs").getDurationMs()).isEqualTo(2000);
        assertThat(byCorrelation.get("single").getDurationKind()).isEqualTo(DurationKind.UNKNOWN);
        assertThat(byCorrelation.get("single").getDurationMs()).isNull();
        assertThat(byCorrelation.get("nots").getDurationKind()).isEqualTo(DurationKind.UNKNOWN);
        assertThat(byCorrelation.get("obs").getStatus()).isEqualTo(TraceStatus.OK);
    }
}
