package dev.traceflow.traces.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.traceflow.parsing.ParsedEvent;
import dev.traceflow.traces.domain.DurationKind;
import dev.traceflow.traces.domain.Trace;
import dev.traceflow.traces.domain.TraceStatus;

/**
 * Incrementally aggregates the events of one trace while a file is being ingested. Only summary information is
 * retained, so memory usage grows with the number of traces and not with the number of events.
 */
final class TraceAccumulator {

    private record SeenService(Instant firstTs, long firstSeq) {
    }

    /** A span without parent: candidate for the root of the request. */
    private static final class RootCandidate {
        String spanId;
        String service;
        String operation;
        Instant firstTs;
        long firstSeq;
        Long reportedDurationMs;
        Integer httpStatus;
    }

    final UUID traceRef;
    final String key;
    String correlationId;
    String traceId;
    int events;
    int errors;
    int warnings;
    Instant firstTs;
    Instant lastTs;
    int timestampedEvents;
    long firstSeq = Long.MAX_VALUE;
    String firstService;
    String firstOperation;
    Integer lastHttpStatusOfFirstService;
    private final Map<String, SeenService> services = new HashMap<>();
    private final Map<String, RootCandidate> roots = new LinkedHashMap<>();

    TraceAccumulator(UUID traceRef, String key) {
        this.traceRef = traceRef;
        this.key = key;
    }

    void add(ParsedEvent e, long seq) {
        events++;
        if (e.isError()) {
            errors++;
        } else if (e.isWarning()) {
            warnings++;
        }
        if (correlationId == null) {
            correlationId = e.getCorrelationId();
        }
        if (traceId == null) {
            traceId = e.getTraceId();
        }
        Instant ts = e.getTimestamp();
        if (ts != null) {
            timestampedEvents++;
            firstTs = firstTs == null || ts.isBefore(firstTs) ? ts : firstTs;
            lastTs = lastTs == null || ts.isAfter(lastTs) ? ts : lastTs;
        }
        if (e.getService() != null) {
            services.merge(e.getService(), new SeenService(ts, seq), (a, b) -> earlier(a, b));
        }
        if (isEarlier(ts, seq, firstTsOfFirstEvent, firstSeq)) {
            firstTsOfFirstEvent = ts;
            firstSeq = seq;
            firstService = e.getService();
            firstOperation = e.getOperation();
        }
        if (e.getService() != null && e.getService().equals(firstService) && e.getHttpStatus() != null) {
            lastHttpStatusOfFirstService = e.getHttpStatus();
        }
        if (e.getSpanId() != null && e.getParentSpanId() == null) {
            RootCandidate root = roots.computeIfAbsent(e.getSpanId(), id -> {
                var c = new RootCandidate();
                c.spanId = id;
                c.firstSeq = seq;
                return c;
            });
            if (root.service == null) {
                root.service = e.getService();
            }
            if (root.operation == null) {
                root.operation = e.getOperation();
            }
            if (ts != null && (root.firstTs == null || ts.isBefore(root.firstTs))) {
                root.firstTs = ts;
            }
            if (e.getDurationMs() != null) {
                root.reportedDurationMs = root.reportedDurationMs == null
                        ? e.getDurationMs() : Math.max(root.reportedDurationMs, e.getDurationMs());
            }
            if (e.getHttpStatus() != null) {
                root.httpStatus = e.getHttpStatus();
            }
        }
    }

    private Instant firstTsOfFirstEvent;

    void mergeFrom(TraceAccumulator other) {
        events += other.events;
        errors += other.errors;
        warnings += other.warnings;
        timestampedEvents += other.timestampedEvents;
        if (correlationId == null) {
            correlationId = other.correlationId;
        }
        if (traceId == null) {
            traceId = other.traceId;
        }
        if (other.firstTs != null && (firstTs == null || other.firstTs.isBefore(firstTs))) {
            firstTs = other.firstTs;
        }
        if (other.lastTs != null && (lastTs == null || other.lastTs.isAfter(lastTs))) {
            lastTs = other.lastTs;
        }
        other.services.forEach((name, seen) -> services.merge(name, seen, TraceAccumulator::earlier));
        if (isEarlier(other.firstTsOfFirstEvent, other.firstSeq, firstTsOfFirstEvent, firstSeq)) {
            firstTsOfFirstEvent = other.firstTsOfFirstEvent;
            firstSeq = other.firstSeq;
            firstService = other.firstService;
            firstOperation = other.firstOperation;
            lastHttpStatusOfFirstService = other.lastHttpStatusOfFirstService;
        }
        other.roots.forEach(roots::putIfAbsent);
    }

    Trace toTrace(java.util.UUID analysisId, String sourceKind) {
        var trace = new Trace();
        trace.setId(traceRef);
        trace.setAnalysisId(analysisId);
        trace.setSourceKind(sourceKind);
        trace.setCorrelationKey(key);
        trace.setCorrelationId(correlationId);
        trace.setTraceId(traceId);
        trace.setStartedAt(firstTs);
        trace.setEndedAt(lastTs);
        trace.setEventCount(events);
        trace.setErrorCount(errors);
        trace.setWarnCount(warnings);
        trace.setCreatedAt(Instant.now());

        RootCandidate root = roots.values().stream()
                .min(Comparator.comparing((RootCandidate c) -> c.firstTs, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingLong(c -> c.firstSeq))
                .orElse(null);
        Integer httpStatus;
        if (root != null) {
            trace.setRootService(root.service);
            trace.setRootOperation(root.operation);
            httpStatus = root.httpStatus;
        } else {
            trace.setRootService(firstService);
            trace.setRootOperation(firstOperation);
            httpStatus = lastHttpStatusOfFirstService;
        }
        trace.setHttpStatus(httpStatus);

        if (root != null && root.reportedDurationMs != null) {
            trace.setDurationMs(root.reportedDurationMs);
            trace.setDurationKind(DurationKind.REPORTED);
        } else if (timestampedEvents >= 2 && firstTs != null && lastTs != null) {
            trace.setDurationMs(Duration.between(firstTs, lastTs).toMillis());
            trace.setDurationKind(DurationKind.OBSERVED);
        } else {
            trace.setDurationKind(DurationKind.UNKNOWN);
        }

        if (errors > 0 || (httpStatus != null && httpStatus >= 500)) {
            trace.setStatus(TraceStatus.ERROR);
        } else if (warnings > 0 || (httpStatus != null && httpStatus >= 400)) {
            trace.setStatus(TraceStatus.WARNING);
        } else {
            trace.setStatus(TraceStatus.OK);
        }

        String ordered = services.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, SeenService> en) -> en.getValue().firstTs(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingLong(en -> en.getValue().firstSeq()))
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
        trace.setServices(ordered.length() > 2000 ? ordered.substring(0, 2000) : ordered);
        trace.setServiceCount(services.size());
        return trace;
    }

    private static SeenService earlier(SeenService a, SeenService b) {
        return isEarlier(b.firstTs(), b.firstSeq(), a.firstTs(), a.firstSeq()) ? b : a;
    }

    /** Orders by timestamp when both are known, otherwise by ingestion sequence. */
    private static boolean isEarlier(Instant ts, long seq, Instant otherTs, long otherSeq) {
        if (otherSeq == Long.MAX_VALUE) {
            return true;
        }
        if (ts != null && otherTs != null && !ts.equals(otherTs)) {
            return ts.isBefore(otherTs);
        }
        return seq < otherSeq;
    }
}
