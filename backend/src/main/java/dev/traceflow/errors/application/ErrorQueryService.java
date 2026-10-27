package dev.traceflow.errors.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.traceflow.common.Scope;
import dev.traceflow.common.web.NotFoundException;
import dev.traceflow.common.web.PageResponse;
import dev.traceflow.common.web.Paging;
import dev.traceflow.errors.application.ErrorDtos.ErrorDetailDto;
import dev.traceflow.errors.application.ErrorDtos.ErrorFacetsDto;
import dev.traceflow.errors.application.ErrorDtos.ErrorGroupDto;
import dev.traceflow.events.application.EventDto;
import dev.traceflow.events.application.EventFilter;
import dev.traceflow.events.application.EventMapper;
import dev.traceflow.events.domain.LogEvent;
import dev.traceflow.events.domain.LogEventRepository;
import dev.traceflow.parsing.StackTraceAnalyzer;
import dev.traceflow.traces.application.TraceDtos.TraceSummaryDto;
import dev.traceflow.traces.domain.TraceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErrorQueryService {

    private final LogEventRepository events;
    private final TraceRepository traces;
    private final EventMapper mapper;
    private final EntityManager em;

    public ErrorQueryService(LogEventRepository events, TraceRepository traces, EventMapper mapper, EntityManager em) {
        this.events = events;
        this.traces = traces;
        this.mapper = mapper;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public PageResponse<EventDto> list(EventFilter filter, int page, int size) {
        var pageable = Paging.of(page, size, Sort.by(Sort.Order.desc("timestamp").nullsLast(), Sort.Order.desc("seq")));
        return PageResponse.of(events.findAll(filter.withErrorsOnly().toSpecification(), pageable),
                e -> mapper.toDto(e, false));
    }

    /** Groups error events by fingerprint, most frequent first. */
    @Transactional(readOnly = true)
    public PageResponse<ErrorGroupDto> groups(EventFilter filter, int page, int size) {
        Specification<LogEvent> spec = filter.withErrorsOnly().toSpecification();
        var cb = em.getCriteriaBuilder();

        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<LogEvent> root = query.from(LogEvent.class);
        Expression<Long> count = cb.count(root);
        query.multiselect(root.get("fingerprint").alias("fingerprint"),
                        cb.greatest(root.<String>get("exceptionClass")).alias("exceptionClass"),
                        count.alias("occurrences"),
                        cb.least(root.<Instant>get("timestamp")).alias("firstSeen"),
                        cb.greatest(root.<Instant>get("timestamp")).alias("lastSeen"))
                .where(spec.toPredicate(root, query, cb), cb.isNotNull(root.get("fingerprint")))
                .groupBy(root.get("fingerprint"))
                .orderBy(cb.desc(count), cb.asc(root.get("fingerprint")));
        var pageable = Paging.of(page, size, Sort.unsorted());
        List<Tuple> rows = em.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<LogEvent> countRoot = countQuery.from(LogEvent.class);
        countQuery.select(cb.countDistinct(countRoot.get("fingerprint")))
                .where(spec.toPredicate(countRoot, countQuery, cb), cb.isNotNull(countRoot.get("fingerprint")));
        long total = em.createQuery(countQuery).getSingleResult();

        List<ErrorGroupDto> groups = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            String fingerprint = row.get("fingerprint", String.class);
            Specification<LogEvent> groupSpec = spec.and((r, q, c) -> c.equal(r.get("fingerprint"), fingerprint));
            LogEvent sample = events.findAll(groupSpec,
                            PageRequest.of(0, 1, Sort.by(Sort.Order.desc("timestamp").nullsLast(), Sort.Order.desc("seq"))))
                    .stream().findFirst().orElse(null);
            groups.add(new ErrorGroupDto(fingerprint, row.get("exceptionClass", String.class),
                    sample == null ? null : firstLine(sample.getExceptionMessage() != null
                            ? sample.getExceptionMessage() : sample.getMessage()),
                    row.get("occurrences", Long.class), row.get("firstSeen", Instant.class),
                    row.get("lastSeen", Instant.class), servicesOf(groupSpec),
                    sample == null ? null : sample.getId(), sample == null ? null : sample.getTraceRef(),
                    sample == null ? null : sample.getCorrelationId()));
        }
        int totalPages = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(groups, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages);
    }

    @Transactional(readOnly = true)
    public ErrorDetailDto detail(UUID eventId) {
        LogEvent event = events.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event " + eventId + " not found"));
        TraceSummaryDto trace = event.getTraceRef() == null ? null
                : traces.findById(event.getTraceRef()).map(TraceSummaryDto::of).orElse(null);
        long similar = event.getFingerprint() == null ? 1
                : events.count((r, q, c) -> c.equal(r.get("fingerprint"), event.getFingerprint()));
        return new ErrorDetailDto(mapper.toDto(event, true), StackTraceAnalyzer.analyze(event.getStackTrace()), trace,
                similar);
    }

    @Transactional(readOnly = true)
    public ErrorFacetsDto facets(Scope scope) {
        var filter = new EventFilter(scope, null, null, null, null, null, null, null, null, null, null, true, null, null);
        Specification<LogEvent> spec = filter.toSpecification();
        return new ErrorFacetsDto(distinct(spec, "service"), distinct(spec, "exceptionClass"), events.count(spec));
    }

    private List<String> servicesOf(Specification<LogEvent> spec) {
        return distinct(spec, "service").stream().limit(10).toList();
    }

    private List<String> distinct(Specification<LogEvent> spec, String attribute) {
        var cb = em.getCriteriaBuilder();
        CriteriaQuery<String> query = cb.createQuery(String.class);
        Root<LogEvent> root = query.from(LogEvent.class);
        query.select(root.get(attribute)).distinct(true)
                .where(spec.toPredicate(root, query, cb), cb.isNotNull(root.get(attribute)))
                .orderBy(cb.asc(root.get(attribute)));
        return em.createQuery(query).setMaxResults(500).getResultList();
    }

    private static String firstLine(String value) {
        if (value == null) {
            return null;
        }
        String line = value.lines().findFirst().orElse("");
        return line.length() > 300 ? line.substring(0, 300) + "…" : line;
    }
}
