package dev.traceflow.analytics.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.traceflow.analytics.application.AnalyticsDtos.DurationStats;

/** Percentile computation (nearest-rank) with minimum sample sizes, so no statistic is reported without evidence. */
public final class DurationMath {

    public static final int MIN_SAMPLES_P50 = 5;
    public static final int MIN_SAMPLES_P95 = 20;
    public static final int MIN_SAMPLES_P99 = 100;

    private DurationMath() {
    }

    public static DurationStats stats(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return new DurationStats(0, null, null, null, null, null, null, "No duration samples available.");
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        long sum = 0;
        for (long v : sorted) {
            sum += v;
        }
        int n = sorted.size();
        Long p50 = n >= MIN_SAMPLES_P50 ? percentile(sorted, 50) : null;
        Long p95 = n >= MIN_SAMPLES_P95 ? percentile(sorted, 95) : null;
        Long p99 = n >= MIN_SAMPLES_P99 ? percentile(sorted, 99) : null;
        String note = null;
        if (p99 == null) {
            note = "Percentiles require at least " + MIN_SAMPLES_P50 + " (P50), " + MIN_SAMPLES_P95 + " (P95) and "
                    + MIN_SAMPLES_P99 + " (P99) samples; " + n + " available.";
        }
        return new DurationStats(n, Math.round((double) sum / n), sorted.getLast(), sorted.getFirst(), p50, p95, p99,
                note);
    }

    /** Nearest-rank percentile over a sorted list. */
    public static long percentile(List<Long> sorted, double p) {
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
    }
}
