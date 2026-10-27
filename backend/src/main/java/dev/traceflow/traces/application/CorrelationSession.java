package dev.traceflow.traces.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.traceflow.parsing.ParsedEvent;
import dev.traceflow.traces.domain.Trace;

/**
 * Groups events of one analysis into traces.
 * <p>
 * The grouping key is the correlationId when present, otherwise the traceId. When an event carries both, the two
 * identifiers are linked (union-find), so events that only carry the traceId still join the correlated trace.
 * Events without any identifier stay uncorrelated: TraceFlow does not guess membership from timing or proximity.
 */
public final class CorrelationSession {

    /** Result of assigning an event to a trace. */
    public record Assignment(UUID traceRef, String correlationKey) {
    }

    /** Two traces that turned out to be the same one; events of {@code from} must be moved to {@code to}. */
    public record Merge(UUID from, UUID to) {
    }

    private static final int MAX_KEY = 128;

    private final Map<String, String> parent = new HashMap<>();
    private final Map<String, TraceAccumulator> traces = new LinkedHashMap<>();
    private final List<Merge> merges = new ArrayList<>();

    public Assignment assign(ParsedEvent event, long seq) {
        String correlationKey = event.getCorrelationId() == null ? null : key("c:", event.getCorrelationId());
        String traceKey = event.getTraceId() == null ? null : key("t:", event.getTraceId());
        if (correlationKey == null && traceKey == null) {
            return null;
        }
        String rootC = correlationKey == null ? null : find(correlationKey);
        String rootT = traceKey == null ? null : find(traceKey);
        String root;
        if (rootC == null && rootT == null) {
            root = correlationKey != null ? correlationKey : traceKey;
            parent.put(root, root);
            traces.put(root, new TraceAccumulator(UUID.randomUUID(), root));
            if (correlationKey != null && traceKey != null) {
                parent.put(traceKey, root);
            }
        } else if (rootC != null && rootT != null && !rootC.equals(rootT)) {
            root = union(rootC, rootT);
        } else {
            root = rootC != null ? rootC : rootT;
            if (correlationKey != null && rootC == null) {
                parent.put(correlationKey, root);
            }
            if (traceKey != null && rootT == null) {
                parent.put(traceKey, root);
            }
        }
        TraceAccumulator acc = traces.get(root);
        acc.add(event, seq);
        return new Assignment(acc.traceRef, acc.key);
    }

    public List<Trace> buildTraces(UUID analysisId, String sourceKind) {
        return traces.values().stream().map(a -> a.toTrace(analysisId, sourceKind)).toList();
    }

    public List<Merge> merges() {
        return List.copyOf(merges);
    }

    public int traceCount() {
        return traces.size();
    }

    private String find(String key) {
        String p = parent.get(key);
        if (p == null) {
            return null;
        }
        if (p.equals(key)) {
            return key;
        }
        String root = find(p);
        parent.put(key, root);
        return root;
    }

    /** Merges the smaller trace into the larger one and records the move of already written events. */
    private String union(String a, String b) {
        TraceAccumulator ta = traces.get(a);
        TraceAccumulator tb = traces.get(b);
        TraceAccumulator keep = ta.events >= tb.events ? ta : tb;
        TraceAccumulator drop = keep == ta ? tb : ta;
        keep.mergeFrom(drop);
        traces.remove(drop.key);
        parent.put(drop.key, keep.key);
        // rewrite earlier merges that targeted the dropped trace
        merges.replaceAll(m -> m.to().equals(drop.traceRef) ? new Merge(m.from(), keep.traceRef) : m);
        merges.add(new Merge(drop.traceRef, keep.traceRef));
        return keep.key;
    }

    private static String key(String prefix, String value) {
        String key = prefix + value.trim();
        return key.length() > MAX_KEY ? key.substring(0, MAX_KEY) : key;
    }
}
