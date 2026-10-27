package dev.traceflow.traces.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.traceflow.events.domain.LogEvent;
import dev.traceflow.traces.application.TraceDtos.EdgeDto;
import dev.traceflow.traces.application.TraceDtos.NodeDto;
import dev.traceflow.traces.application.TraceDtos.RelationshipSummary;
import dev.traceflow.traces.application.TraceDtos.ServiceWindowDto;
import dev.traceflow.traces.application.TraceDtos.SpanDto;
import dev.traceflow.traces.application.TraceDtos.TimelineEventDto;
import dev.traceflow.traces.application.TraceDtos.TraceDetailDto;
import dev.traceflow.traces.application.TraceDtos.TraceSummaryDto;
import dev.traceflow.traces.domain.Trace;

/**
 * Reconstructs the call graph, waterfall and timeline of a trace from its events.
 * <p>
 * Evidence rules - TraceFlow never invents relations, durations or dependencies:
 * <ul>
 *     <li><b>CONFIRMED</b>: a span whose {@code parentSpanId} references a span of another service, or a span/event
 *     that explicitly declares its {@code peerService}.</li>
 *     <li><b>INFERRED</b>: a log message explicitly mentions calling/publishing to another service of the same trace,
 *     with no confirmed relation between them.</li>
 *     <li><b>UNKNOWN</b>: services without any evidence are listed as unlinked; no edge is drawn.</li>
 * </ul>
 * Without any relation the trace is presented as an event timeline only.
 */
public final class TraceGraphBuilder {

    private static final String CALL_VERBS = "call(?:ing|ed|s)?|request(?:ing|ed)?|send(?:ing)?|sent|forward(?:ing|ed)?|"
            + "publish(?:ing|ed)?|invok(?:e|ing|ed)|delegat(?:e|ing|ed)|contact(?:ing|ed)?|query(?:ing)?|routing|"
            + "proxy(?:ing)?|dispatch(?:ing|ed)?";
    private static final Pattern ASYNC_VERBS = Pattern.compile("(?i)publish|sent|send|dispatch");

    private final Function<String, Map<String, Object>> attributeReader;

    public TraceGraphBuilder(Function<String, Map<String, Object>> attributeReader) {
        this.attributeReader = attributeReader;
    }

    private static final class SpanAcc {
        String spanId;
        String parentSpanId;
        String service;
        String operation;
        String kind;
        String peerService;
        String peerType;
        String messageId;
        Instant firstTs;
        Instant lastTs;
        Instant durationEventTs;
        Instant publishTs;
        Long reportedMs;
        Integer httpStatus;
        boolean error;
        boolean warn;
        int events;
        long firstSeq = Long.MAX_VALUE;
    }

    private static final class NodeAcc {
        final String name;
        String type = "SERVICE";
        boolean virtual;
        int events;
        int errors;
        int warns;
        Instant first;
        Instant last;
        Integer httpStatus;
        final Set<String> operations = new LinkedHashSet<>();

        NodeAcc(String name) {
            this.name = name;
        }
    }

    private static final class EdgeAcc {
        final String source;
        final String target;
        String relation;
        String mode = "SYNC";
        int calls;
        int errors;
        final List<Long> durations = new ArrayList<>();
        final List<Long> latencies = new ArrayList<>();
        final Set<String> operations = new LinkedHashSet<>();
        String evidence;

        EdgeAcc(String source, String target) {
            this.source = source;
            this.target = target;
        }
    }

    public TraceDetailDto build(Trace trace, List<LogEvent> events, long totalEvents) {
        Map<String, NodeAcc> nodes = new LinkedHashMap<>();
        Map<String, SpanAcc> spans = new LinkedHashMap<>();
        for (LogEvent e : events) {
            if (e.getService() != null) {
                NodeAcc node = nodes.computeIfAbsent(e.getService(), NodeAcc::new);
                node.events++;
                if (e.isError()) {
                    node.errors++;
                } else if ("WARN".equals(e.getLevel())) {
                    node.warns++;
                }
                if (e.getTimestamp() != null) {
                    node.first = node.first == null || e.getTimestamp().isBefore(node.first) ? e.getTimestamp() : node.first;
                    node.last = node.last == null || e.getTimestamp().isAfter(node.last) ? e.getTimestamp() : node.last;
                }
                if (e.getOperation() != null && node.operations.size() < 12) {
                    node.operations.add(e.getOperation());
                }
                if (e.getHttpStatus() != null && (e.getParentSpanId() == null || node.httpStatus == null)) {
                    node.httpStatus = e.getHttpStatus();
                }
                Object type = attributeReader.apply(e.getAttributes()).get("serviceType");
                if (type != null) {
                    node.type = normalizeType(type.toString());
                }
            }
            if (e.getSpanId() != null) {
                SpanAcc s = spans.computeIfAbsent(e.getSpanId(), id -> {
                    var acc = new SpanAcc();
                    acc.spanId = id;
                    return acc;
                });
                s.events++;
                s.firstSeq = Math.min(s.firstSeq, e.getSeq());
                s.service = s.service == null ? e.getService() : s.service;
                s.operation = s.operation == null ? e.getOperation() : s.operation;
                s.parentSpanId = s.parentSpanId == null ? e.getParentSpanId() : s.parentSpanId;
                s.kind = s.kind == null ? e.getSpanKind() : s.kind;
                s.peerService = s.peerService == null ? e.getPeerService() : s.peerService;
                s.peerType = s.peerType == null ? e.getPeerType() : s.peerType;
                if (e.getMessageId() != null) {
                    s.messageId = s.messageId == null ? e.getMessageId() : s.messageId;
                    if (e.getTimestamp() != null && (s.publishTs == null || e.getTimestamp().isBefore(s.publishTs))) {
                        s.publishTs = e.getTimestamp();
                    }
                }
                if (e.getTimestamp() != null) {
                    s.firstTs = s.firstTs == null || e.getTimestamp().isBefore(s.firstTs) ? e.getTimestamp() : s.firstTs;
                    s.lastTs = s.lastTs == null || e.getTimestamp().isAfter(s.lastTs) ? e.getTimestamp() : s.lastTs;
                }
                if (e.getDurationMs() != null && (s.reportedMs == null || e.getDurationMs() > s.reportedMs)) {
                    s.reportedMs = e.getDurationMs();
                    s.durationEventTs = e.getTimestamp();
                }
                if (e.getHttpStatus() != null) {
                    s.httpStatus = e.getHttpStatus();
                }
                s.error |= e.isError();
                s.warn |= "WARN".equals(e.getLevel());
            }
        }

        Instant origin = java.util.stream.Stream.concat(
                        events.stream().map(LogEvent::getTimestamp),
                        spans.values().stream().map(TraceGraphBuilder::spanStart))
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(trace.getStartedAt());

        // virtual nodes for declared peers that do not log themselves (databases, external APIs)
        for (LogEvent e : events) {
            if (e.getPeerService() != null && !nodes.containsKey(e.getPeerService())) {
                NodeAcc peer = new NodeAcc(e.getPeerService());
                peer.virtual = true;
                peer.type = normalizeType(e.getPeerType() == null ? "external" : e.getPeerType());
                nodes.put(peer.name, peer);
            }
            if (e.getPeerService() != null) {
                NodeAcc peer = nodes.get(e.getPeerService());
                if (peer.virtual) {
                    if (e.getTimestamp() != null) {
                        peer.first = peer.first == null || e.getTimestamp().isBefore(peer.first) ? e.getTimestamp() : peer.first;
                        peer.last = peer.last == null || e.getTimestamp().isAfter(peer.last) ? e.getTimestamp() : peer.last;
                    }
                    if (e.isError()) {
                        peer.errors++;
                    }
                    if (e.getOperation() != null && peer.operations.size() < 12) {
                        peer.operations.add(e.getOperation());
                    }
                }
            }
        }

        Map<String, EdgeAcc> edges = new LinkedHashMap<>();
        int missingParents = 0;
        Map<String, Integer> depthCache = new HashMap<>();
        Set<String> spansWithChildInPeer = new java.util.HashSet<>();
        for (SpanAcc s : spans.values()) {
            if (s.parentSpanId == null) {
                continue;
            }
            SpanAcc parent = spans.get(s.parentSpanId);
            if (parent == null) {
                missingParents++;
                continue;
            }
            if (parent.service != null && s.service != null && !parent.service.equals(s.service)) {
                EdgeAcc edge = edges.computeIfAbsent(parent.service + "->" + s.service,
                        k -> new EdgeAcc(parent.service, s.service));
                edge.relation = "CONFIRMED";
                edge.evidence = "Span parent/child relationship (parentSpanId)";
                boolean async = isAsync(parent, s);
                if (async) {
                    edge.mode = "ASYNC";
                    Instant published = parent.publishTs != null ? parent.publishTs : parent.firstTs;
                    Instant consumed = spanStart(s);
                    if (published != null && consumed != null) {
                        edge.latencies.add(Duration.between(published, consumed).toMillis());
                    }
                }
                edge.calls++;
                if (s.error) {
                    edge.errors++;
                }
                if (s.reportedMs != null) {
                    edge.durations.add(s.reportedMs);
                }
                if (s.operation != null && edge.operations.size() < 10) {
                    edge.operations.add(s.operation);
                }
                if (s.service.equals(parent.peerService)) {
                    spansWithChildInPeer.add(parent.spanId);
                }
            }
        }
        // explicitly declared peers (client spans or events naming their target)
        for (SpanAcc s : spans.values()) {
            if (s.peerService == null || s.service == null || s.peerService.equals(s.service)
                    || spansWithChildInPeer.contains(s.spanId)) {
                continue;
            }
            EdgeAcc edge = edges.computeIfAbsent(s.service + "->" + s.peerService, k -> new EdgeAcc(s.service, s.peerService));
            if (edge.relation == null) {
                edge.relation = "CONFIRMED";
                edge.evidence = "Client span explicitly declares peerService=" + s.peerService;
            }
            if ("PRODUCER".equals(s.kind) || s.messageId != null) {
                edge.mode = "ASYNC";
            }
            edge.calls++;
            if (s.error) {
                edge.errors++;
            }
            if (s.reportedMs != null) {
                edge.durations.add(s.reportedMs);
            }
            if (s.operation != null && edge.operations.size() < 10) {
                edge.operations.add(s.operation);
            }
        }
        for (LogEvent e : events) {
            if (e.getSpanId() == null && e.getPeerService() != null && e.getService() != null
                    && !e.getPeerService().equals(e.getService())) {
                EdgeAcc edge = edges.computeIfAbsent(e.getService() + "->" + e.getPeerService(),
                        k -> new EdgeAcc(e.getService(), e.getPeerService()));
                if (edge.relation == null) {
                    edge.relation = "CONFIRMED";
                    edge.evidence = "Event explicitly declares peerService=" + e.getPeerService();
                }
                edge.calls++;
                if (e.isError()) {
                    edge.errors++;
                }
            }
        }
        inferFromMessages(events, nodes.keySet(), edges);

        // build DTOs
        List<SpanDto> spanDtos = new ArrayList<>();
        List<SpanAcc> orderedSpans = spans.values().stream()
                .sorted(Comparator.comparing(TraceGraphBuilder::spanStart, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingLong(s -> s.firstSeq))
                .toList();
        for (SpanAcc s : orderedSpans) {
            Instant start = spanStart(s);
            Instant end;
            Long duration;
            String kind;
            if (s.reportedMs != null && start != null) {
                end = start.plusMillis(s.reportedMs);
                duration = s.reportedMs;
                kind = "REPORTED";
            } else if (s.firstTs != null && s.lastTs != null && s.events > 1) {
                end = s.lastTs;
                duration = Duration.between(s.firstTs, s.lastTs).toMillis();
                kind = "OBSERVED";
            } else {
                end = s.lastTs;
                duration = null;
                kind = "UNKNOWN";
            }
            SpanAcc parent = s.parentSpanId == null ? null : spans.get(s.parentSpanId);
            spanDtos.add(new SpanDto(s.spanId, s.parentSpanId, s.service, s.operation, s.kind, start, end,
                    offset(origin, start), duration, kind, s.error ? "ERROR" : s.warn ? "WARNING" : "OK", s.httpStatus,
                    s.events, s.peerService, depth(s, spans, depthCache, 0), parent != null && isAsync(parent, s),
                    s.parentSpanId != null && parent == null));
        }

        List<ServiceWindowDto> windows = nodes.values().stream()
                .filter(n -> !n.virtual && n.first != null)
                .sorted(Comparator.comparing((NodeAcc n) -> n.first))
                .map(n -> new ServiceWindowDto(n.name, n.first, n.last, offset(origin, n.first),
                        Duration.between(n.first, n.last).toMillis(), n.events, status(n)))
                .toList();

        List<NodeDto> nodeDtos = nodes.values().stream()
                .map(n -> new NodeDto(n.name, n.name, n.type, n.virtual, n.events, n.errors, n.warns, status(n),
                        n.first, n.last, List.copyOf(n.operations), n.httpStatus))
                .toList();
        List<EdgeDto> edgeDtos = edges.values().stream()
                .map(ea -> new EdgeDto(ea.source + "->" + ea.target, ea.source, ea.target, ea.relation, ea.mode,
                        ea.calls, ea.errors, average(ea.durations), average(ea.latencies), List.copyOf(ea.operations),
                        ea.evidence))
                .toList();

        Set<String> linked = new java.util.HashSet<>();
        edgeDtos.forEach(ed -> {
            linked.add(ed.source());
            linked.add(ed.target());
        });
        List<String> unlinked = nodes.keySet().stream().filter(n -> !linked.contains(n)).toList();
        int confirmed = (int) edgeDtos.stream().filter(ed -> ed.relation().equals("CONFIRMED")).count();
        int inferred = edgeDtos.size() - confirmed;
        boolean timelineOnly = edgeDtos.isEmpty();

        List<TimelineEventDto> timeline = events.stream()
                .map(e -> new TimelineEventDto(e.getId(), e.getTimestamp(), offset(origin, e.getTimestamp()),
                        e.getService(), e.getLevel(), cut(e.getMessage(), 600), e.getSpanId(), e.getSpanKind(),
                        e.getOperation(), e.getExceptionClass(), e.getDurationMs(), e.getHttpStatus(), e.isError(),
                        e.getFileId(), e.getLineStart(), e.getMessageId(), e.getPeerService()))
                .toList();

        List<String> notes = new ArrayList<>();
        String timingBasis = !spans.isEmpty() ? "SPANS" : !windows.isEmpty() ? "SERVICE_WINDOWS" : "NONE";
        if (timelineOnly) {
            notes.add("The logs contain no span relationships or explicit call evidence between services. "
                    + "TraceFlow shows the event timeline without asserting causal relationships.");
        }
        if (inferred > 0) {
            notes.add(inferred + " relation(s) are INFERRED from log messages that mention calling another service; "
                    + "they are not confirmed by span data.");
        }
        if (!unlinked.isEmpty() && !timelineOnly) {
            notes.add("No relation evidence for: " + String.join(", ", unlinked) + ".");
        }
        if (missingParents > 0) {
            notes.add(missingParents + " span(s) reference a parent span that is not present in the analyzed logs.");
        }
        if ("SERVICE_WINDOWS".equals(timingBasis)) {
            notes.add("No span identifiers found: the waterfall shows observed windows (first to last log line) "
                    + "per service, which are not request durations.");
        }
        notes.add("REPORTED durations come from duration fields written by the application; OBSERVED durations are "
                + "the distance between log timestamps.");
        boolean truncated = totalEvents > events.size();
        if (truncated) {
            notes.add("Only the first " + events.size() + " of " + totalEvents + " events are shown.");
        }
        return new TraceDetailDto(TraceSummaryDto.of(trace), nodeDtos, edgeDtos, spanDtos, windows, timingBasis,
                timeline, new RelationshipSummary(confirmed, inferred, unlinked, missingParents, timelineOnly),
                errorOrigin(spanDtos, events), notes, truncated, totalEvents);
    }

    /**
     * The deepest span reporting an error is the most specific failure point of the call tree. Without span data,
     * the first error event in time is reported, with a weaker justification.
     */
    private static TraceDtos.ErrorOriginDto errorOrigin(List<SpanDto> spans, List<LogEvent> events) {
        SpanDto deepest = spans.stream()
                .filter(s -> "ERROR".equals(s.status()))
                .max(Comparator.comparingInt(SpanDto::depth)
                        .thenComparing(SpanDto::start, Comparator.nullsFirst(Comparator.<Instant>naturalOrder()).reversed()))
                .orElse(null);
        if (deepest != null) {
            UUID eventId = events.stream()
                    .filter(e -> deepest.spanId().equals(e.getSpanId()) && e.isError())
                    .map(LogEvent::getId).findFirst().orElse(null);
            String target = deepest.peerService() != null ? " (calling " + deepest.peerService() + ")" : "";
            return new TraceDtos.ErrorOriginDto(deepest.service(), deepest.spanId(), eventId,
                    "Deepest span with an error in the call tree" + target);
        }
        return events.stream().filter(LogEvent::isError).findFirst()
                .map(e -> new TraceDtos.ErrorOriginDto(e.getService(), e.getSpanId(), e.getId(),
                        "First error event in time (no span hierarchy available)"))
                .orElse(null);
    }

    private void inferFromMessages(List<LogEvent> events, Set<String> services, Map<String, EdgeAcc> edges) {
        if (services.size() < 2) {
            return;
        }
        Map<String, Pattern> patterns = new HashMap<>();
        for (String target : services) {
            patterns.put(target, Pattern.compile("(?i)\\b(" + CALL_VERBS + ")\\b[^.;\\n]{0,40}?(?<![\\w-])"
                    + Pattern.quote(target) + "(?![\\w-])"));
        }
        for (LogEvent e : events) {
            if (e.getService() == null || e.getMessage() == null) {
                continue;
            }
            for (String target : services) {
                if (target.equals(e.getService())) {
                    continue;
                }
                if (hasAnyEdge(edges, e.getService(), target, "CONFIRMED")) {
                    continue;
                }
                Matcher m = patterns.get(target).matcher(e.getMessage());
                if (m.find()) {
                    EdgeAcc edge = edges.computeIfAbsent(e.getService() + "->" + target,
                            k -> new EdgeAcc(e.getService(), target));
                    edge.relation = "INFERRED";
                    if (ASYNC_VERBS.matcher(m.group(1)).find()) {
                        edge.mode = "ASYNC";
                    }
                    edge.calls++;
                    if (edge.evidence == null) {
                        edge.evidence = "Log message: \"" + cut(e.getMessage().lines().findFirst().orElse(""), 160) + "\"";
                    }
                }
            }
        }
    }

    private static boolean hasAnyEdge(Map<String, EdgeAcc> edges, String a, String b, String relation) {
        EdgeAcc ab = edges.get(a + "->" + b);
        EdgeAcc ba = edges.get(b + "->" + a);
        return (ab != null && relation.equals(ab.relation)) || (ba != null && relation.equals(ba.relation));
    }

    private static boolean isAsync(SpanAcc parent, SpanAcc child) {
        return "PRODUCER".equals(parent.kind) || "CONSUMER".equals(child.kind)
                || (child.messageId != null && Objects.equals(child.messageId, parent.messageId));
    }

    /** Start of a span: when a duration is reported by the completion event, start = completion - duration. */
    private static Instant spanStart(SpanAcc s) {
        if (s.reportedMs != null && s.durationEventTs != null) {
            Instant derived = s.durationEventTs.minusMillis(s.reportedMs);
            return s.firstTs == null || derived.isBefore(s.firstTs) ? derived : s.firstTs;
        }
        return s.firstTs;
    }

    private static int depth(SpanAcc s, Map<String, SpanAcc> spans, Map<String, Integer> cache, int guard) {
        if (s.parentSpanId == null || guard > 64) {
            return 0;
        }
        Integer cached = cache.get(s.spanId);
        if (cached != null) {
            return cached;
        }
        SpanAcc parent = spans.get(s.parentSpanId);
        int d = parent == null ? 1 : depth(parent, spans, cache, guard + 1) + 1;
        cache.put(s.spanId, d);
        return d;
    }

    private static String status(NodeAcc n) {
        return n.errors > 0 ? "ERROR" : n.warns > 0 ? "WARNING" : "OK";
    }

    private static String normalizeType(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "database", "db", "sql", "postgres", "postgresql", "mysql" -> "DATABASE";
            case "broker", "queue", "topic", "messaging", "kafka", "rabbitmq" -> "BROKER";
            case "gateway", "api-gateway" -> "GATEWAY";
            case "cache", "redis" -> "CACHE";
            case "service" -> "SERVICE";
            default -> "EXTERNAL";
        };
    }

    private static Long offset(Instant origin, Instant ts) {
        return origin == null || ts == null ? null : Duration.between(origin, ts).toMillis();
    }

    private static Long average(List<Long> values) {
        if (values.isEmpty()) {
            return null;
        }
        return Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
