package dev.traceflow.traces.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import dev.traceflow.common.Scope;
import dev.traceflow.common.web.NotFoundException;
import dev.traceflow.common.web.PageResponse;
import dev.traceflow.common.web.Paging;
import dev.traceflow.config.TraceflowProperties;
import dev.traceflow.events.application.EventMapper;
import dev.traceflow.events.domain.LogEventRepository;
import dev.traceflow.traces.application.TraceDtos.SearchResultDto;
import dev.traceflow.traces.application.TraceDtos.TraceDetailDto;
import dev.traceflow.traces.application.TraceDtos.TraceSummaryDto;
import dev.traceflow.traces.domain.Trace;
import dev.traceflow.traces.domain.TraceRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TraceQueryService {

    public record TraceFilter(Scope scope, UUID analysisId, String q, String service, String status, Instant from,
                              Instant to) {
    }

    private final TraceRepository traces;
    private final LogEventRepository events;
    private final TraceGraphBuilder graphBuilder;
    private final TraceflowProperties properties;

    public TraceQueryService(TraceRepository traces, LogEventRepository events, EventMapper eventMapper,
                             TraceflowProperties properties) {
        this.traces = traces;
        this.events = events;
        this.properties = properties;
        this.graphBuilder = new TraceGraphBuilder(eventMapper::attributes);
    }

    @Transactional(readOnly = true)
    public PageResponse<TraceSummaryDto> list(TraceFilter filter, int page, int size, String sort) {
        var pageable = Paging.of(page, size, sortOf(sort));
        return PageResponse.of(traces.findAll(specification(filter), pageable), TraceSummaryDto::of);
    }

    @Transactional(readOnly = true)
    public TraceDetailDto detail(UUID id) {
        Trace trace = traces.findById(id).orElseThrow(() -> new NotFoundException("Trace " + id + " not found"));
        int limit = properties.parsing().maxEventsPerTraceDetail();
        var traceEvents = events.findTraceEvents(id, PageRequest.of(0, limit));
        long total = traceEvents.size() < limit ? traceEvents.size() : events.countByTraceRef(id);
        return graphBuilder.build(trace, traceEvents, total);
    }

    @Transactional(readOnly = true)
    public List<TraceSummaryDto> byCorrelationId(String correlationId) {
        return traces.findByCorrelationIdOrderByStartedAtDesc(correlationId).stream().map(TraceSummaryDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<TraceSummaryDto> byTraceId(String traceId) {
        return traces.findByTraceIdOrderByStartedAtDesc(traceId).stream().map(TraceSummaryDto::of).toList();
    }

    /** Global search: exact or prefix match on correlationId / traceId. */
    @Transactional(readOnly = true)
    public SearchResultDto search(String q) {
        String query = q == null ? "" : q.trim();
        if (query.length() < 2) {
            return new SearchResultDto(List.of(), query);
        }
        var found = traces.findTop10ByCorrelationIdStartingWithOrTraceIdStartingWithOrderByStartedAtDesc(query, query);
        return new SearchResultDto(found.stream().map(TraceSummaryDto::of).toList(), query);
    }

    static Specification<Trace> specification(TraceFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.scope() != null && f.scope().sourceKind() != null) {
                p.add(cb.equal(root.get("sourceKind"), f.scope().sourceKind()));
            }
            if (f.analysisId() != null) {
                p.add(cb.equal(root.get("analysisId"), f.analysisId()));
            }
            if (f.status() != null && !f.status().isBlank()) {
                p.add(cb.equal(root.get("status").as(String.class), f.status().toUpperCase(Locale.ROOT)));
            }
            if (f.service() != null && !f.service().isBlank()) {
                String s = f.service().trim();
                // services column is a comma separated ordered list
                p.add(cb.or(cb.equal(root.get("services"), s), cb.like(root.get("services"), s + ",%"),
                        cb.like(root.get("services"), "%," + s), cb.like(root.get("services"), "%," + s + ",%")));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("startedAt"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("startedAt"), f.to()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String q = f.q().trim();
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(root.get("correlationId"), q + "%"), cb.like(root.get("traceId"), q + "%"),
                        cb.like(cb.lower(root.get("rootOperation")), like),
                        cb.like(cb.lower(root.get("services")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    static Sort sortOf(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.desc("startedAt").nullsLast());
        }
        boolean desc = sort.startsWith("-");
        String field = desc ? sort.substring(1) : sort;
        String property = switch (field) {
            case "startedAt", "durationMs", "eventCount", "errorCount", "status", "rootService" -> field;
            default -> "startedAt";
        };
        return Sort.by(desc ? Sort.Order.desc(property).nullsLast() : Sort.Order.asc(property).nullsLast());
    }
}
