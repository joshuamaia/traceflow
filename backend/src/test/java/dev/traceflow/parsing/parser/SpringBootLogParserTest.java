package dev.traceflow.parsing.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import dev.traceflow.parsing.ParsedEvent;
import dev.traceflow.parsing.TestParsing;
import org.junit.jupiter.api.Test;

class SpringBootLogParserTest {

    private final SpringBootLogParser parser = new SpringBootLogParser();

    @Test
    void parsesBoot3LayoutWithApplicationNameAndCorrelationSection() {
        String line = "2026-10-27T10:15:30.123Z  INFO 4242 --- [order-service] [nio-8080-exec-1] "
                + "[4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7] d.t.OrderController     : Received order 42";

        ParsedEvent e = parser.parse(line, TestParsing.context(1));

        assertThat(e).isNotNull();
        assertThat(e.getTimestamp()).isEqualTo(Instant.parse("2026-10-27T10:15:30.123Z"));
        assertThat(e.getLevel()).isEqualTo("INFO");
        assertThat(e.getService()).isEqualTo("order-service");
        assertThat(e.getThread()).isEqualTo("nio-8080-exec-1");
        assertThat(e.getLogger()).isEqualTo("d.t.OrderController");
        assertThat(e.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(e.getSpanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(e.getMessage()).isEqualTo("Received order 42");
        assertThat(e.getParser()).isEqualTo(SpringBootLogParser.NAME);
    }

    @Test
    void parsesBoot2SleuthLayout() {
        String line = "2026-10-27 10:15:30.123  WARN [payment-service,4bf92f3577b34da6,00f067aa0ba902b8,true] 4242 --- "
                + "[main] d.t.PaymentClient : slow response took 1200 ms";

        ParsedEvent e = parser.parse(line, TestParsing.context(7));

        assertThat(e.getService()).isEqualTo("payment-service");
        assertThat(e.getTraceId()).isEqualTo("4bf92f3577b34da6");
        assertThat(e.getSpanId()).isEqualTo("00f067aa0ba902b8");
        assertThat(e.getThread()).isEqualTo("main");
        assertThat(e.getLevel()).isEqualTo("WARN");
        assertThat(e.getDurationMs()).isEqualTo(1200);
        assertThat(e.getLineStart()).isEqualTo(7);
    }

    @Test
    void distinguishesThreadAndCorrelationSectionWithoutApplicationName() {
        String line = "2026-10-27T10:15:30.123+02:00 ERROR 1 --- [exec-3] [4bf92f3577b34da6-00f067aa0ba902b7] "
                + "c.a.Svc : failed correlationId=req-77";

        ParsedEvent e = parser.parse(line, TestParsing.context(1));

        assertThat(e.getService()).isNull();
        assertThat(e.getThread()).isEqualTo("exec-3");
        assertThat(e.getTraceId()).isEqualTo("4bf92f3577b34da6");
        assertThat(e.getCorrelationId()).isEqualTo("req-77");
        assertThat(e.getTimestamp()).isEqualTo(Instant.parse("2026-10-27T08:15:30.123Z"));
    }

    @Test
    void returnsNullForOtherFormats() {
        assertThat(parser.parse("2026-10-27 10:15:30 INFO [svc] hello", TestParsing.context(1))).isNull();
    }
}
