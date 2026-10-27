package dev.traceflow.parsing.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import dev.traceflow.parsing.ParseContext;
import dev.traceflow.parsing.ParsedEvent;
import dev.traceflow.parsing.TestParsing;
import org.junit.jupiter.api.Test;

class ClassicTextLogParserTest {

    private final ClassicTextLogParser parser = new ClassicTextLogParser();

    @Test
    void parsesTheReferenceExampleWithServiceAndCorrelationId() {
        ParsedEvent e = parser.parse(
                "2026-10-27 10:15:30 INFO [order-service] [correlationId=abc123] Starting order processing",
                TestParsing.context(1));

        assertThat(e).isNotNull();
        assertThat(e.getTimestamp()).isEqualTo(Instant.parse("2026-10-27T10:15:30Z"));
        assertThat(e.getLevel()).isEqualTo("INFO");
        assertThat(e.getService()).isEqualTo("order-service");
        assertThat(e.getCorrelationId()).isEqualTo("abc123");
        assertThat(e.getMessage()).isEqualTo("Starting order processing");
    }

    @Test
    void parsesLogbackDefaultLayoutWithThreadAndLogger() {
        ParsedEvent e = parser.parse(
                "2026-10-27 10:15:30.123 [http-nio-8080-exec-1] ERROR dev.traceflow.OrderService - Payment failed",
                TestParsing.context(3));

        assertThat(e.getThread()).isEqualTo("http-nio-8080-exec-1");
        assertThat(e.getLevel()).isEqualTo("ERROR");
        assertThat(e.getLogger()).isEqualTo("dev.traceflow.OrderService");
        assertThat(e.getMessage()).isEqualTo("Payment failed");
        assertThat(e.isError()).isTrue();
    }

    @Test
    void extractsTraceAndSpanIdsFromKeyValueSections() {
        ParsedEvent e = parser.parse(
                "2026-10-27T10:15:30Z WARN [payment-service] [traceId=4bf92f3577b34da6 spanId=00f067aa0ba902b7] Retry",
                TestParsing.context(1));

        assertThat(e.getTraceId()).isEqualTo("4bf92f3577b34da6");
        assertThat(e.getSpanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(e.getService()).isEqualTo("payment-service");
    }

    @Test
    void keepsEventWithInvalidTimestamp() {
        ParsedEvent e = parser.parse("2026-13-45 99:99:99 WARN [svc-a] broken clock", TestParsing.context(1));

        assertThat(e).isNotNull();
        assertThat(e.getTimestamp()).isNull();
        assertThat(e.isTimestampInvalid()).isTrue();
        assertThat(e.getTimestampRaw()).isEqualTo("2026-13-45 99:99:99");
    }

    @Test
    void usesFileNameServiceAsFallback() {
        ParsedEvent e = parser.parse("2026-10-27 10:15:30 INFO started", new ParseContext(1, TestParsing.UTC, "billing-service"));

        assertThat(e.getService()).isEqualTo("billing-service");
        assertThat(e.isServiceFromFileName()).isTrue();
    }

    @Test
    void requiresALevelToAvoidFalsePositives() {
        assertThat(parser.parse("2026-10-27 10:15:30 something happened", TestParsing.context(1))).isNull();
    }
}
