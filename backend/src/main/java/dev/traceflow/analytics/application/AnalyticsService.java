package dev.traceflow.analytics.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.traceflow.analytics.application.AnalyticsDtos.AnalyticsDto;
import dev.traceflow.analytics.application.AnalyticsDtos.CountItem;
import dev.traceflow.analytics.application.AnalyticsDtos.DashboardDto;
import dev.traceflow.analytics.application.AnalyticsDtos.DurationStats;
import dev.traceflow.analytics.application.AnalyticsDtos.ExceptionStatDto;
import dev.traceflow.analytics.application.AnalyticsDtos.HistogramBucket;
import dev.traceflow.analytics.application.AnalyticsDtos.OperationDurationDto;
import dev.traceflow.analytics.application.AnalyticsDtos.ServiceFailureDto;
import dev.traceflow.analytics.application.AnalyticsDtos.TimeBucket;
import dev.traceflow.analytics.application.AnalyticsDtos.TimeSeries;
import dev.traceflow.common.Scope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Metrics derived exclusively from persisted events and traces. Request durations (traces) and operation durations
 * (events carrying an explicit duration) are always reported separately.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final int MAX_DURATION_SAMPLES = 50_000;
    private static final long[] HISTOGRAM_EDGES = {0, 50, 100, 250, 500, 1000, 2500, 5000, 10000};

    private final EntityManager em;

    public AnalyticsService(EntityManager em) {
        this.em = em;
    }

    public DashboardDto dashboard(Scope scope) {
        String kind = scope.sourceKind();
        long traces = count("select count(t) from Trace t where 1=1" + kindClause("t", kind), kind, null, null, "t");
        long events = count("select count(e) from LogEvent e where 1=1" + kindClause("e", kind), kind, null, null, "e");
        long errors = count("select count(e) from LogEvent e where e.error = true" + kindClause("e", kind), kind, null,
                null, "e");
        long warns = count("select count(e) from LogEvent e where e.level = 'WARN'" + kindClause("e", kind), kind, null,
                null, "e");
        long services = count("select count(distinct e.service) from LogEvent e where e.service is not null"
                + kindClause("e", kind), kind, null, null, "e");

        Object[] range = range(kind, null, null);
        Instant min = (Instant) range[0];
        Instant max = (Instant) range[1];
        TimeSeries series = null;
        if (max != null) {
            // events per minute over the most recent two hours of data
            Instant from = min.isAfter(max.minus(Duration.ofHours(2))) ? min : max.minus(Duration.ofHours(2));
            series = timeSeries(kind, from, max, 60, "1 minute");
        }

        List<Long> durations = traceDurations(kind, null, null, "REPORTED");
        String basis = "REPORTED";
        if (durations.isEmpty()) {
            durations = traceDurations(kind, null, null, "OBSERVED");
            basis = durations.isEmpty() ? null : "OBSERVED";
        }
        Long avg = durations.isEmpty() ? null
                : Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0));

        return new DashboardDto(scope.name(), traces, events, errors, warns, services, avg, basis, durations.size(),
                series, errorsByService(kind, null, null, 10), severity(kind, null, null), topExceptions(kind, 10),
                histogram(durations), min, max);
    }

    public AnalyticsDto analytics(Scope scope, Instant fromParam, Instant toParam) {
        String kind = scope.sourceKind();
        Object[] range = range(kind, fromParam, toParam);
        Instant from = fromParam != null ? fromParam : (Instant) range[0];
        Instant to = toParam != null ? toParam : (Instant) range[1];
        long events = count("select count(e) from LogEvent e where 1=1" + kindClause("e", kind) + rangeClause("e.timestamp", fromParam, toParam),
                kind, fromParam, toParam, "e");
        List<String> notes = new ArrayList<>();
        if (events == 0) {
            notes.add("No events found for the selected scope and period. Import logs or run a demo scenario.");
            return new AnalyticsDto(scope.name(), from, to, false, 0, 0, 0, 0, 0, 0, null, List.of(), List.of(),
                    List.of(), DurationMath.stats(List.of()), null, List.of(), notes);
        }
        long errors = count("select count(e) from LogEvent e where e.error = true" + kindClause("e", kind)
                + rangeClause("e.timestamp", fromParam, toParam), kind, fromParam, toParam, "e");
        long traces = count("select count(t) from Trace t where 1=1" + kindClause("t", kind)
                + rangeClause("t.startedAt", fromParam, toParam), kind, fromParam, toParam, "t");
        long errorTraces = count("select count(t) from Trace t where t.status = dev.traceflow.traces.domain.TraceStatus.ERROR"
                + kindClause("t", kind) + rangeClause("t.startedAt", fromParam, toParam), kind, fromParam, toParam, "t");

        TimeSeries volume = null;
        if (from != null && to != null) {
            long span = Math.max(1, Duration.between(from, to).toSeconds());
            int bucket = span <= 600 ? 10 : span <= 7200 ? 60 : span <= 172800 ? 3600 : 86400;
            String label = switch (bucket) {
                case 10 -> "10 seconds";
                case 60 -> "1 minute";
                case 3600 -> "1 hour";
                default -> "1 day";
            };
            volume = timeSeries(kind, from, to, bucket, label);
        } else {
            notes.add("Events have no valid timestamps: time based charts are not available.");
        }

        List<ServiceFailureDto> failures = serviceFailures(kind, fromParam, toParam);
        List<Long> reported = traceDurations(kind, fromParam, toParam, "REPORTED");
        List<Long> observed = traceDurations(kind, fromParam, toParam, "OBSERVED");
        DurationStats requestStats;
        String requestBasis;
        if (!reported.isEmpty()) {
            requestStats = DurationMath.stats(reported);
            requestBasis = "REPORTED";
            if (!observed.isEmpty()) {
                notes.add(observed.size() + " trace(s) without a reported root duration were excluded from request "
                        + "duration statistics.");
            }
        } else {
            requestStats = DurationMath.stats(observed);
            requestBasis = observed.isEmpty() ? null : "OBSERVED";
            if (!observed.isEmpty()) {
                notes.add("Request durations are OBSERVED (first to last log timestamp of each trace), "
                        + "no explicit durations were logged.");
            }
        }
        List<OperationDurationDto> operations = operationDurations(kind, fromParam, toParam);
        if (operations.isEmpty()) {
            notes.add("No events carry an explicit duration: operation duration statistics are not available.");
        }
        return new AnalyticsDto(scope.name(), from, to, true, events, errors, ratio(errors, events), traces,
                errorTraces, ratio(errorTraces, traces), volume, severity(kind, fromParam, toParam), failures,
                recurringExceptions(kind, fromParam, toParam), requestStats, requestBasis, operations, notes);
    }

    // ---------------------------------------------------------------- queries

    private TimeSeries timeSeries(String kind, Instant from, Instant to, int bucketSeconds, String label) {
        // bucketSeconds is a server-side constant, never user input
        String bucketExpr = "floor(extract(epoch from e.timestamp) / " + bucketSeconds + ")";
        String jpql = "select " + bucketExpr + ", count(e), sum(case when e.error = true then 1 else 0 end), "
                + "sum(case when e.level = 'WARN' then 1 else 0 end) from LogEvent e "
                + "where e.timestamp >= :from and e.timestamp <= :to" + kindClause("e", kind)
                + " group by " + bucketExpr + " order by " + bucketExpr;
        TypedQuery<Object[]> query = em.createQuery(jpql, Object[].class)
                .setParameter("from", from).setParameter("to", to);
        if (kind != null) {
            query.setParameter("kind", kind);
        }
        Map<Long, TimeBucket> byBucket = new LinkedHashMap<>();
        for (Object[] row : query.getResultList()) {
            long bucket = ((Number) row[0]).longValue();
            byBucket.put(bucket, new TimeBucket(Instant.ofEpochSecond(bucket * bucketSeconds),
                    ((Number) row[1]).longValue(), number(row[2]), number(row[3])));
        }
        // fill gaps so charts show empty periods truthfully
        List<TimeBucket> buckets = new ArrayList<>();
        long first = from.getEpochSecond() / bucketSeconds;
        long last = to.getEpochSecond() / bucketSeconds;
        if (last - first > 2000) {
            buckets.addAll(byBucket.values());
        } else {
            for (long b = first; b <= last; b++) {
                buckets.add(byBucket.getOrDefault(b, new TimeBucket(Instant.ofEpochSecond(b * bucketSeconds), 0, 0, 0)));
            }
        }
        return new TimeSeries(bucketSeconds, label, buckets);
    }

    private Object[] range(String kind, Instant from, Instant to) {
        TypedQuery<Object[]> q = em.createQuery("select min(e.timestamp), max(e.timestamp) from LogEvent e where "
                + "e.timestamp is not null" + kindClause("e", kind) + rangeClause("e.timestamp", from, to), Object[].class);
        bind(q, kind, from, to);
        return q.getSingleResult();
    }

    private List<CountItem> errorsByService(String kind, Instant from, Instant to, int limit) {
        TypedQuery<Object[]> q = em.createQuery("select coalesce(e.service, '(unknown)'), count(e) from LogEvent e "
                + "where e.error = true" + kindClause("e", kind) + rangeClause("e.timestamp", from, to)
                + " group by coalesce(e.service, '(unknown)') order by count(e) desc", Object[].class);
        bind(q, kind, from, to);
        return q.setMaxResults(limit).getResultList().stream()
                .map(r -> new CountItem((String) r[0], ((Number) r[1]).longValue())).toList();
    }

    private List<CountItem> severity(String kind, Instant from, Instant to) {
        TypedQuery<Object[]> q = em.createQuery("select e.level, count(e) from LogEvent e where 1=1"
                + kindClause("e", kind) + rangeClause("e.timestamp", from, to) + " group by e.level", Object[].class);
        bind(q, kind, from, to);
        List<String> order = List.of("FATAL", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "UNKNOWN");
        return q.getResultList().stream()
                .map(r -> new CountItem((String) r[0], ((Number) r[1]).longValue()))
                .sorted((a, b) -> Integer.compare(order.indexOf(a.key()), order.indexOf(b.key())))
                .toList();
    }

    private List<CountItem> topExceptions(String kind, int limit) {
        TypedQuery<Object[]> q = em.createQuery("select e.exceptionClass, count(e) from LogEvent e "
                + "where e.exceptionClass is not null" + kindClause("e", kind)
                + " group by e.exceptionClass order by count(e) desc", Object[].class);
        bind(q, kind, null, null);
        return q.setMaxResults(limit).getResultList().stream()
                .map(r -> new CountItem((String) r[0], ((Number) r[1]).longValue())).toList();
    }

    private List<ServiceFailureDto> serviceFailures(String kind, Instant from, Instant to) {
        TypedQuery<Object[]> q = em.createQuery("select e.service, count(e), sum(case when e.error = true then 1 else 0 end) "
                + "from LogEvent e where e.service is not null" + kindClause("e", kind)
                + rangeClause("e.timestamp", from, to) + " group by e.service", Object[].class);
        bind(q, kind, from, to);
        return q.getResultList().stream()
                .map(r -> {
                    long total = ((Number) r[1]).longValue();
                    long err = number(r[2]);
                    return new ServiceFailureDto((String) r[0], total, err, ratio(err, total));
                })
                .sorted((a, b) -> a.errors() != b.errors() ? Long.compare(b.errors(), a.errors())
                        : Long.compare(b.events(), a.events()))
                .limit(20)
                .toList();
    }

    private List<ExceptionStatDto> recurringExceptions(String kind, Instant from, Instant to) {
        TypedQuery<Object[]> q = em.createQuery("select e.fingerprint, max(e.exceptionClass), count(e) from LogEvent e "
                + "where e.fingerprint is not null and e.exceptionClass is not null" + kindClause("e", kind)
                + rangeClause("e.timestamp", from, to) + " group by e.fingerprint order by count(e) desc", Object[].class);
        bind(q, kind, from, to);
        List<ExceptionStatDto> result = new ArrayList<>();
        for (Object[] r : q.setMaxResults(10).getResultList()) {
            TypedQuery<String> sq = em.createQuery("select distinct e.service from LogEvent e where e.fingerprint = :fp "
                    + "and e.service is not null" + kindClause("e", kind), String.class).setParameter("fp", r[0]);
            if (kind != null) {
                sq.setParameter("kind", kind);
            }
            result.add(new ExceptionStatDto((String) r[1], (String) r[0], ((Number) r[2]).longValue(),
                    sq.setMaxResults(10).getResultList()));
        }
        return result;
    }

    private List<OperationDurationDto> operationDurations(String kind, Instant from, Instant to) {
        TypedQuery<Object[]> q = em.createQuery("select coalesce(e.service, '(unknown)'), coalesce(e.operation, '(unnamed)'), "
                + "e.durationMs from LogEvent e where e.durationMs is not null" + kindClause("e", kind)
                + rangeClause("e.timestamp", from, to), Object[].class);
        bind(q, kind, from, to);
        Map<String, List<Long>> grouped = new LinkedHashMap<>();
        for (Object[] r : q.setMaxResults(MAX_DURATION_SAMPLES).getResultList()) {
            grouped.computeIfAbsent(r[0] + "\u0000" + r[1], k -> new ArrayList<>()).add(((Number) r[2]).longValue());
        }
        return grouped.entrySet().stream()
                .map(en -> {
                    String[] key = en.getKey().split("\u0000", 2);
                    return new OperationDurationDto(key[0], key[1], DurationMath.stats(en.getValue()));
                })
                .sorted((a, b) -> Long.compare(b.stats().samples(), a.stats().samples()))
                .limit(50)
                .toList();
    }

    private List<Long> traceDurations(String kind, Instant from, Instant to, String durationKind) {
        TypedQuery<Long> q = em.createQuery("select t.durationMs from Trace t where t.durationMs is not null "
                + "and t.durationKind = dev.traceflow.traces.domain.DurationKind." + durationKind
                + kindClause("t", kind) + rangeClause("t.startedAt", from, to) + " order by t.startedAt desc", Long.class);
        bind(q, kind, from, to);
        return q.setMaxResults(MAX_DURATION_SAMPLES).getResultList();
    }

    private static List<HistogramBucket> histogram(List<Long> durations) {
        List<HistogramBucket> buckets = new ArrayList<>();
        if (durations.isEmpty()) {
            return buckets;
        }
        for (int i = 0; i < HISTOGRAM_EDGES.length; i++) {
            long lower = HISTOGRAM_EDGES[i];
            Long upper = i + 1 < HISTOGRAM_EDGES.length ? HISTOGRAM_EDGES[i + 1] : null;
            long count = durations.stream().filter(d -> d >= lower && (upper == null || d < upper)).count();
            String label = upper == null ? "≥ " + format(lower) : format(lower) + "–" + format(upper);
            buckets.add(new HistogramBucket(label, lower, upper, count));
        }
        return buckets;
    }

    private static String format(long ms) {
        return ms >= 1000 ? (ms % 1000 == 0 ? ms / 1000 + "s" : (ms / 1000.0) + "s") : ms + "ms";
    }

    private long count(String jpql, String kind, Instant from, Instant to, String alias) {
        TypedQuery<Long> q = em.createQuery(jpql, Long.class);
        bind(q, kind, from, to);
        return q.getSingleResult();
    }

    private static void bind(TypedQuery<?> q, String kind, Instant from, Instant to) {
        if (kind != null) {
            q.setParameter("kind", kind);
        }
        if (from != null) {
            q.setParameter("from", from);
        }
        if (to != null) {
            q.setParameter("to", to);
        }
    }

    private static String kindClause(String alias, String kind) {
        return kind == null ? "" : " and " + alias + ".sourceKind = :kind";
    }

    private static String rangeClause(String field, Instant from, Instant to) {
        return (from == null ? "" : " and " + field + " >= :from") + (to == null ? "" : " and " + field + " <= :to");
    }

    private static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static double ratio(long part, long total) {
        return total == 0 ? 0 : Math.round(part * 10000.0 / total) / 10000.0;
    }
}
