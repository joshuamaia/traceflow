package dev.traceflow.analysis.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.traceflow.traces.application.CorrelationSession;
import dev.traceflow.traces.domain.Trace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * High throughput JDBC writes used by the ingestion pipeline (JPA is used for reads).
 */
@Repository
public class IngestionStore {

    /** Column values of one event row, already truncated to the column sizes. */
    public record EventRow(UUID id, UUID analysisId, UUID fileId, String sourceKind, UUID traceRef,
                           String correlationKey, long seq, int lineStart, int lineEnd, Instant timestamp,
                           String tsRaw, String level, String service, String thread, String logger,
                           String correlationId, String traceId, String spanId, String parentSpanId,
                           String spanKind, String operation, String peerService, String peerType, String messageId,
                           String message, String exceptionClass, String exceptionMessage, String stackTrace,
                           Long durationMs, Integer httpStatus, boolean error, String fingerprint,
                           String attributes, String raw, String parser) {
    }

    public record ProblemRow(UUID id, UUID analysisId, UUID fileId, int lineNumber, String severity, String reason,
                             String raw) {
    }

    private static final String INSERT_EVENT = """
            INSERT INTO log_events (id, analysis_id, file_id, source_kind, trace_ref, correlation_key, seq,
                line_start, line_end, event_ts, ts_raw, level, service, thread, logger, correlation_id, trace_id,
                span_id, parent_span_id, span_kind, operation, peer_service, peer_type, message_id, message,
                exception_class, exception_message, stack_trace, duration_ms, http_status, is_error, fingerprint,
                attributes, raw, parser)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?)""";

    private static final String INSERT_PROBLEM = """
            INSERT INTO parse_errors (id, analysis_id, file_id, line_number, severity, reason, raw)
            VALUES (?, ?, ?, ?, ?, ?, ?)""";

    private static final String INSERT_TRACE = """
            INSERT INTO traces (id, analysis_id, source_kind, correlation_key, correlation_id, trace_id, root_service,
                root_operation, started_at, ended_at, duration_ms, duration_kind, status, http_status, event_count,
                error_count, warn_count, service_count, services, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private final JdbcTemplate jdbc;

    public IngestionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertEvents(List<EventRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> args = new ArrayList<>(rows.size());
        for (EventRow r : rows) {
            args.add(new Object[]{r.id(), r.analysisId(), r.fileId(), r.sourceKind(), r.traceRef(), r.correlationKey(),
                    r.seq(), r.lineStart(), r.lineEnd(), odt(r.timestamp()), r.tsRaw(), r.level(), r.service(),
                    r.thread(), r.logger(), r.correlationId(), r.traceId(), r.spanId(), r.parentSpanId(), r.spanKind(),
                    r.operation(), r.peerService(), r.peerType(), r.messageId(), r.message(), r.exceptionClass(),
                    r.exceptionMessage(), r.stackTrace(), r.durationMs(), r.httpStatus(), r.error(), r.fingerprint(),
                    r.attributes(), r.raw(), r.parser()});
        }
        jdbc.batchUpdate(INSERT_EVENT, args);
    }

    public void insertProblems(List<ProblemRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> args = rows.stream()
                .map(r -> new Object[]{r.id(), r.analysisId(), r.fileId(), r.lineNumber(), r.severity(), r.reason(),
                        r.raw()})
                .toList();
        jdbc.batchUpdate(INSERT_PROBLEM, args);
    }

    public void insertTraces(List<Trace> traces) {
        for (int from = 0; from < traces.size(); from += 500) {
            List<Object[]> args = traces.subList(from, Math.min(traces.size(), from + 500)).stream()
                    .map(t -> new Object[]{t.getId(), t.getAnalysisId(), t.getSourceKind(), t.getCorrelationKey(),
                            t.getCorrelationId(), t.getTraceId(), t.getRootService(), t.getRootOperation(),
                            odt(t.getStartedAt()), odt(t.getEndedAt()), t.getDurationMs(), t.getDurationKind().name(),
                            t.getStatus().name(), t.getHttpStatus(), t.getEventCount(), t.getErrorCount(),
                            t.getWarnCount(), t.getServiceCount(), t.getServices(), odt(t.getCreatedAt())})
                    .toList();
            jdbc.batchUpdate(INSERT_TRACE, args);
        }
    }

    /** Moves events of merged traces to the surviving trace. */
    public void applyMerges(List<CorrelationSession.Merge> merges) {
        if (merges.isEmpty()) {
            return;
        }
        List<Object[]> args = merges.stream().map(m -> new Object[]{m.to(), m.from()}).toList();
        jdbc.batchUpdate("UPDATE log_events SET trace_ref = ? WHERE trace_ref = ?", args);
    }

    /** Removes everything derived from the files of an analysis (used before (re)processing). */
    public void deleteDerivedData(UUID analysisId) {
        jdbc.update("DELETE FROM log_events WHERE analysis_id = ?", analysisId);
        jdbc.update("DELETE FROM parse_errors WHERE analysis_id = ?", analysisId);
        jdbc.update("DELETE FROM traces WHERE analysis_id = ?", analysisId);
    }

    public void deleteAnalysis(UUID analysisId) {
        jdbc.update("DELETE FROM scenario_runs WHERE analysis_id = ?", analysisId);
        deleteDerivedData(analysisId);
        jdbc.update("DELETE FROM analysis_files WHERE analysis_id = ?", analysisId);
        jdbc.update("DELETE FROM analyses WHERE id = ?", analysisId);
    }

    public long countErrorEvents(UUID analysisId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM log_events WHERE analysis_id = ? AND is_error = TRUE",
                Long.class, analysisId);
        return count == null ? 0 : count;
    }

    private static OffsetDateTime odt(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
