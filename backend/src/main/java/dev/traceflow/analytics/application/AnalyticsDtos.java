package dev.traceflow.analytics.application;

import java.time.Instant;
import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record CountItem(String key, long count) {
    }

    public record TimeBucket(Instant start, long events, long errors, long warnings) {
    }

    public record TimeSeries(int bucketSeconds, String bucketLabel, List<TimeBucket> buckets) {
    }

    public record HistogramBucket(String label, long fromMs, Long toMs, long count) {
    }

    /**
     * Distribution statistics. Percentiles are {@code null} when there are not enough samples
     * (P50 needs 5, P95 needs 20, P99 needs 100 samples).
     */
    public record DurationStats(long samples, Long avgMs, Long maxMs, Long minMs, Long p50Ms, Long p95Ms, Long p99Ms,
                                String note) {
    }

    public record DashboardDto(String scope, long totalTraces, long totalEvents, long errorEvents, long warnEvents,
                               long servicesCount, Long avgTraceDurationMs, String avgDurationBasis,
                               long tracesWithDuration, TimeSeries eventsOverTime, List<CountItem> errorsByService,
                               List<CountItem> severityDistribution, List<CountItem> topExceptions,
                               List<HistogramBucket> requestDurationHistogram, Instant dataFrom, Instant dataTo) {
    }

    public record ServiceFailureDto(String service, long events, long errors, double errorRate) {
    }

    public record OperationDurationDto(String service, String operation, DurationStats stats) {
    }

    public record ExceptionStatDto(String exceptionClass, String fingerprint, long occurrences, List<String> services) {
    }

    public record AnalyticsDto(String scope, Instant from, Instant to, boolean hasData, long totalEvents,
                               long errorEvents, double eventErrorRate, long totalTraces, long errorTraces,
                               double traceErrorRate, TimeSeries volume, List<CountItem> severityDistribution,
                               List<ServiceFailureDto> serviceFailures, List<ExceptionStatDto> recurringExceptions,
                               DurationStats requestDurations, String requestDurationBasis,
                               List<OperationDurationDto> operationDurations, List<String> notes) {
    }
}
