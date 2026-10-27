package dev.traceflow.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogStreamParserTest {

    @Test
    void attachesMultilineStackTraceWithCausesToThePreviousEvent() {
        var result = TestParsing.parse("""
                2026-10-27 10:15:31.500 ERROR [order-service] [correlationId=abc123] Payment failed
                java.lang.IllegalStateException: payment declined
                \tat com.acme.order.PaymentClient.charge(PaymentClient.java:42)
                \tat org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:897)
                Caused by: java.net.SocketTimeoutException: Read timed out
                \tat com.acme.order.HttpTransport.read(HttpTransport.java:19)
                \t... 12 more
                2026-10-27 10:15:32 INFO [order-service] next event
                """);

        assertThat(result.events()).hasSize(2);
        ParsedEvent error = result.events().getFirst();
        assertThat(error.getLineStart()).isEqualTo(1);
        assertThat(error.getLineEnd()).isEqualTo(7);
        assertThat(error.getExceptionClass()).isEqualTo("java.lang.IllegalStateException");
        assertThat(error.getExceptionMessage()).isEqualTo("payment declined");
        assertThat(error.stackTraceText()).contains("Caused by: java.net.SocketTimeoutException").contains("... 12 more");
        assertThat(result.events().get(1).getLineStart()).isEqualTo(8);
        assertThat(result.problems()).isEmpty();
    }

    @Test
    void reportsUnrecognizedAndMalformedLinesWithoutStopping() {
        var result = TestParsing.parse("""
                2026-10-27 10:15:30 INFO [svc-a] first
                this is not a log line
                {"level":"INFO", broken json
                {"level":"INFO","message":"after the problems"}
                2026-13-45 99:99:99 WARN [svc-a] bad clock
                """);

        assertThat(result.events()).extracting(ParsedEvent::getMessage)
                .containsExactly("first", "after the problems", "bad clock");
        assertThat(result.problems()).extracting(ParseProblem::lineNumber).containsExactly(2, 3, 5);
        assertThat(result.problems().get(0).reason()).isEqualTo("Unrecognized log format");
        assertThat(result.problems().get(1).reason()).startsWith("Malformed JSON");
        assertThat(result.problems().get(2).severity()).isEqualTo(ParseProblem.Severity.WARNING);
        assertThat(result.stats().lines()).isEqualTo(5);
    }

    @Test
    void parsesMixedFormatsAndDetectsTheDominantFormat() {
        var result = TestParsing.parse("""
                {"level":"INFO","message":"a"}
                {"level":"INFO","message":"b"}
                {"level":"INFO","message":"c"}
                {"level":"INFO","message":"d"}
                2026-10-27 10:15:30 INFO [svc] text
                """);

        assertThat(result.events()).hasSize(5);
        assertThat(result.stats().detectedFormat()).isEqualTo("JSON");
    }

    @Test
    void streamsJsonArraysAndKeepsSourceLineNumbers() {
        var result = TestParsing.parse("""
                [
                  {"level":"INFO","message":"one","correlationId":"c1"},
                  {
                    "level": "ERROR",
                    "message": "two"
                  },
                  42
                ]""");

        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).getLineStart()).isEqualTo(2);
        assertThat(result.events().get(1).getLineStart()).isEqualTo(3);
        assertThat(result.events().get(1).getLineEnd()).isEqualTo(6);
        assertThat(result.problems()).singleElement().satisfies(p -> assertThat(p.reason()).contains("not a JSON object"));
    }

    @Test
    void appendsIndentedContinuationLinesToTheMessage() {
        var result = TestParsing.parse("""
                2026-10-27 10:15:30 INFO [svc] Executing query
                    SELECT * FROM orders
                """);

        assertThat(result.events()).singleElement()
                .satisfies(e -> assertThat(e.getMessage()).isEqualTo("Executing query\nSELECT * FROM orders"));
    }
}
