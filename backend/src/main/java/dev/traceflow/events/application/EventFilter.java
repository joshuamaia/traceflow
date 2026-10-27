package dev.traceflow.events.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import dev.traceflow.common.Scope;
import dev.traceflow.events.domain.LogEvent;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters shared by the event, error and export endpoints. All criteria are optional and combined with AND.
 */
public record EventFilter(Scope scope, UUID analysisId, UUID fileId, UUID traceRef, String correlationId,
                          String traceId, String service, List<String> levels, String q, Instant from, Instant to,
                          boolean errorsOnly, String exceptionClass, String fingerprint) {

    public EventFilter {
        scope = scope == null ? Scope.ALL : scope;
        levels = levels == null ? List.of() : levels.stream().filter(l -> l != null && !l.isBlank())
                .map(l -> l.trim().toUpperCase(Locale.ROOT)).toList();
        correlationId = blankToNull(correlationId);
        traceId = blankToNull(traceId);
        service = blankToNull(service);
        q = blankToNull(q);
        exceptionClass = blankToNull(exceptionClass);
        fingerprint = blankToNull(fingerprint);
    }

    public EventFilter withErrorsOnly() {
        return new EventFilter(scope, analysisId, fileId, traceRef, correlationId, traceId, service, levels, q, from,
                to, true, exceptionClass, fingerprint);
    }

    public Specification<LogEvent> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (scope.sourceKind() != null) {
                p.add(cb.equal(root.get("sourceKind"), scope.sourceKind()));
            }
            if (analysisId != null) {
                p.add(cb.equal(root.get("analysisId"), analysisId));
            }
            if (fileId != null) {
                p.add(cb.equal(root.get("fileId"), fileId));
            }
            if (traceRef != null) {
                p.add(cb.equal(root.get("traceRef"), traceRef));
            }
            if (correlationId != null) {
                p.add(cb.equal(root.get("correlationId"), correlationId));
            }
            if (traceId != null) {
                p.add(cb.equal(root.get("traceId"), traceId));
            }
            if (service != null) {
                p.add(cb.equal(root.get("service"), service));
            }
            if (!levels.isEmpty()) {
                p.add(root.get("level").in(levels));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            if (errorsOnly) {
                p.add(cb.isTrue(root.get("error")));
            }
            if (exceptionClass != null) {
                p.add(cb.equal(root.get("exceptionClass"), exceptionClass));
            }
            if (fingerprint != null) {
                p.add(cb.equal(root.get("fingerprint"), fingerprint));
            }
            if (q != null) {
                String like = "%" + q.toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("message")), like, '\\'),
                        cb.like(cb.lower(root.get("exceptionClass")), like, '\\'),
                        cb.like(cb.lower(root.get("exceptionMessage")), like, '\\'),
                        cb.like(cb.lower(root.get("logger")), like, '\\'),
                        cb.equal(root.get("correlationId"), q),
                        cb.equal(root.get("traceId"), q)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
