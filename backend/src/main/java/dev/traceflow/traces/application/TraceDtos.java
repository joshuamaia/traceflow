package dev.traceflow.traces.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.traceflow.traces.domain.Trace;

public final class TraceDtos {

    private TraceDtos() {
    }

    public record TraceSummaryDto(UUID id, UUID analysisId, String sourceKind, String correlationId, String traceId,
                                  String rootService, String rootOperation, Instant startedAt, Instant endedAt,
                                  Long durationMs, String durationKind, String status, Integer httpStatus,
                                  int eventCount, int errorCount, int warnCount, List<String> services) {
        public static TraceSummaryDto of(Trace t) {
            return new TraceSummaryDto(t.getId(), t.getAnalysisId(), t.getSourceKind(), t.getCorrelationId(),
                    t.getTraceId(), t.getRootService(), t.getRootOperation(), t.getStartedAt(), t.getEndedAt(),
                    t.getDurationMs(), t.getDurationKind().name(), t.getStatus().name(), t.getHttpStatus(),
                    t.getEventCount(), t.getErrorCount(), t.getWarnCount(), t.serviceList());
        }
    }

    /** Service (or external dependency) participating in a trace. */
    public record NodeDto(String id, String name, String type, boolean virtual, int eventCount, int errorCount,
                          int warnCount, String status, Instant firstSeen, Instant lastSeen, List<String> operations,
                          Integer httpStatus) {
    }

    /**
     * A call between two nodes.
     *
     * @param relation CONFIRMED (span parent/child or explicitly declared peer) or INFERRED (call mentioned in a log
     *                 message); unknown relations are never drawn
     * @param mode     SYNC or ASYNC
     */
    public record EdgeDto(String id, String source, String target, String relation, String mode, int calls,
                          int errors, Long avgDurationMs, Long asyncLatencyMs, List<String> operations,
                          String evidence) {
    }

    public record SpanDto(String spanId, String parentSpanId, String service, String operation, String kind,
                          Instant start, Instant end, Long startOffsetMs, Long durationMs, String durationKind,
                          String status, Integer httpStatus, int eventCount, String peerService, int depth,
                          boolean async, boolean parentMissing) {
    }

    public record ServiceWindowDto(String service, Instant start, Instant end, Long startOffsetMs, Long durationMs,
                                   int eventCount, String status) {
    }

    public record TimelineEventDto(UUID id, Instant timestamp, Long offsetMs, String service, String level,
                                   String message, String spanId, String spanKind, String operation,
                                   String exceptionClass, Long durationMs, Integer httpStatus, boolean error,
                                   UUID fileId, int lineStart, String messageId, String peerService) {
    }

    public record RelationshipSummary(int confirmed, int inferred, List<String> unlinkedServices,
                                      int spansWithMissingParent, boolean timelineOnly) {
    }

    /** Where the failure most likely started, with the evidence used. */
    public record ErrorOriginDto(String service, String spanId, UUID eventId, String reason) {
    }

    public record TraceDetailDto(TraceSummaryDto trace, List<NodeDto> nodes, List<EdgeDto> edges, List<SpanDto> spans,
                                 List<ServiceWindowDto> serviceWindows, String timingBasis,
                                 List<TimelineEventDto> timeline, RelationshipSummary relationships,
                                 ErrorOriginDto errorOrigin, List<String> notes, boolean truncated,
                                 long totalEvents) {
    }

    public record SearchResultDto(List<TraceSummaryDto> traces, String query) {
    }
}
