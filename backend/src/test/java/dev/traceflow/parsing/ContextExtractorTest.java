package dev.traceflow.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContextExtractorTest {

    private ParsedEvent extract(String text) {
        var event = new ParsedEvent("TEST", 1, text);
        ContextExtractor.apply(text, event);
        return event;
    }

    @Test
    void extractsCorrelationTraceAndSpanIds() {
        var e = extract("handled request correlation_id=c-1 traceId: 4bf92f35 span-id=00f0 parentSpanId=aa11");
        assertThat(e.getCorrelationId()).isEqualTo("c-1");
        assertThat(e.getTraceId()).isEqualTo("4bf92f35");
        assertThat(e.getSpanId()).isEqualTo("00f0");
        assertThat(e.getParentSpanId()).isEqualTo("aa11");
    }

    @Test
    void usesRequestIdOnlyWhenNoCorrelationIdIsPresent() {
        assertThat(extract("x-request-id=r-9 done").getCorrelationId()).isEqualTo("r-9");
        assertThat(extract("correlationId=c-1 requestId=r-9").getCorrelationId()).isEqualTo("c-1");
    }

    @Test
    void extractsOnlyExplicitDurationsAndHttpStatuses() {
        assertThat(extract("durationMs=120").getDurationMs()).isEqualTo(120);
        assertThat(extract("duration=1.5s").getDurationMs()).isEqualTo(1500);
        assertThat(extract("completed in 87 ms").getDurationMs()).isEqualTo(87);
        assertThat(extract("took 3 s").getDurationMs()).isEqualTo(3000);
        assertThat(extract("order 1200 processed").getDurationMs()).isNull();
        assertThat(extract("duration=42").getDurationMs()).as("unitless generic duration is ambiguous").isNull();
        assertThat(extract("status=503").getHttpStatus()).isEqualTo(503);
        assertThat(extract("status=COMPLETED").getHttpStatus()).isNull();
        assertThat(extract("status=999").getHttpStatus()).isNull();
    }
}
