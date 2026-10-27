package dev.traceflow.parsing.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import dev.traceflow.parsing.LogParseException;
import dev.traceflow.parsing.ParsedEvent;
import dev.traceflow.parsing.TestParsing;
import org.junit.jupiter.api.Test;

class JsonLogParserTest {

    private final JsonLogParser parser = new JsonLogParser(TestParsing.MAPPER);

    @Test
    void mapsLogstashStyleFields() {
        ParsedEvent e = parser.parse("""
                {"@timestamp":"2026-10-27T10:15:30.250Z","level":"WARN","logger_name":"d.t.Pay","thread_name":"main",\
                "service":"payment-service","correlationId":"abc123","traceId":"t-1","spanId":"s-2","parentSpanId":"s-1",\
                "spanKind":"client","peerService":"postgres-db","durationMs":42,"httpStatus":503,"message":"slow","custom":"x"}""",
                TestParsing.context(5));

        assertThat(e.getTimestamp()).isEqualTo(Instant.parse("2026-10-27T10:15:30.250Z"));
        assertThat(e.getLevel()).isEqualTo("WARN");
        assertThat(e.getLogger()).isEqualTo("d.t.Pay");
        assertThat(e.getThread()).isEqualTo("main");
        assertThat(e.getService()).isEqualTo("payment-service");
        assertThat(e.getCorrelationId()).isEqualTo("abc123");
        assertThat(e.getTraceId()).isEqualTo("t-1");
        assertThat(e.getSpanId()).isEqualTo("s-2");
        assertThat(e.getParentSpanId()).isEqualTo("s-1");
        assertThat(e.getSpanKind()).isEqualTo("CLIENT");
        assertThat(e.getPeerService()).isEqualTo("postgres-db");
        assertThat(e.getDurationMs()).isEqualTo(42);
        assertThat(e.getHttpStatus()).isEqualTo(503);
        assertThat(e.getAttributes()).containsEntry("custom", "x");
        assertThat(e.getLineStart()).isEqualTo(5);
    }

    @Test
    void readsNestedMdcAndEcsFields() {
        ParsedEvent e = parser.parse("""
                {"timestamp":1793095530250,"log":{"level":"error"},"service":{"name":"order-service"},\
                "mdc":{"correlationId":"c-9","tenant":"acme"},"msg":"boom"}""", TestParsing.context(1));

        assertThat(e.getTimestamp()).isEqualTo(Instant.ofEpochMilli(1793095530250L));
        assertThat(e.getLevel()).isEqualTo("ERROR");
        assertThat(e.getService()).isEqualTo("order-service");
        assertThat(e.getCorrelationId()).isEqualTo("c-9");
        assertThat(e.getMessage()).isEqualTo("boom");
        assertThat(e.getAttributes()).containsEntry("mdc.tenant", "acme");
    }

    @Test
    void derivesExceptionFromStackTraceField() {
        ParsedEvent e = parser.parse("""
                {"level":"ERROR","message":"failed","stack_trace":"java.sql.SQLException: duplicate key\\n\\tat a.B.c(B.java:1)"}""",
                TestParsing.context(1));

        assertThat(e.getExceptionClass()).isEqualTo("java.sql.SQLException");
        assertThat(e.getExceptionMessage()).isEqualTo("duplicate key");
        assertThat(e.stackTraceText()).contains("at a.B.c(B.java:1)");
    }

    @Test
    void ignoresNonHttpStatusValues() {
        ParsedEvent e = parser.parse("{\"message\":\"x\",\"status\":\"COMPLETED\"}", TestParsing.context(1));

        assertThat(e.getHttpStatus()).isNull();
        assertThat(e.getAttributes()).containsEntry("status", "COMPLETED");
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> parser.parse("{\"level\":\"INFO\", broken", TestParsing.context(1)))
                .isInstanceOf(LogParseException.class)
                .hasMessageStartingWith("Malformed JSON");
    }
}
