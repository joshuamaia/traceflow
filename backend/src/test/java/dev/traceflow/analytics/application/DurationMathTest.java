package dev.traceflow.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

class DurationMathTest {

    @Test
    void computesNearestRankPercentilesWhenThereAreEnoughSamples() {
        List<Long> values = LongStream.rangeClosed(1, 100).boxed().toList();
        var stats = DurationMath.stats(values);
        assertThat(stats.samples()).isEqualTo(100);
        assertThat(stats.p50Ms()).isEqualTo(50);
        assertThat(stats.p95Ms()).isEqualTo(95);
        assertThat(stats.p99Ms()).isEqualTo(99);
        assertThat(stats.maxMs()).isEqualTo(100);
        assertThat(stats.note()).isNull();
    }

    @Test
    void withholdsPercentilesWithoutEnoughSamples() {
        var stats = DurationMath.stats(List.of(10L, 20L, 30L, 40L, 50L, 60L));
        assertThat(stats.p50Ms()).isEqualTo(30);
        assertThat(stats.p95Ms()).isNull();
        assertThat(stats.p99Ms()).isNull();
        assertThat(stats.avgMs()).isEqualTo(35);
        assertThat(stats.note()).contains("6 available");
        assertThat(DurationMath.stats(List.of(5L)).p50Ms()).isNull();
        assertThat(DurationMath.stats(List.of()).samples()).isZero();
    }
}
