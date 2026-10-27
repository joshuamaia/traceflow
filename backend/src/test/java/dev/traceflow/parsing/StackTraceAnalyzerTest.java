package dev.traceflow.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StackTraceAnalyzerTest {

    @Test
    void findsTheFirstApplicationFrameOfTheRootCause() {
        var analysis = StackTraceAnalyzer.analyze("""
                org.springframework.dao.DataIntegrityViolationException: could not execute statement
                \tat org.springframework.orm.jpa.EntityManagerFactoryUtils.convert(EntityManagerFactoryUtils.java:10)
                \tat com.acme.order.OrderService.place(OrderService.java:88)
                Caused by: java.sql.SQLException: duplicate key
                \tat org.postgresql.core.QueryExecutor.execute(QueryExecutor.java:5)
                \tat com.acme.order.OrderRepository.insert(OrderRepository.java:31)
                \t... 20 more""");

        assertThat(analysis.chain()).hasSize(2);
        assertThat(analysis.rootCause().className()).isEqualTo("java.sql.SQLException");
        assertThat(analysis.probableOrigin()).isNotNull();
        assertThat(analysis.probableOrigin().className()).isEqualTo("com.acme.order.OrderRepository");
        assertThat(analysis.probableOrigin().method()).isEqualTo("insert");
        assertThat(analysis.probableOrigin().location()).isEqualTo("OrderRepository.java:31");
        assertThat(analysis.note()).isNull();
    }

    @Test
    void reportsInsufficientEvidenceWhenOnlyFrameworkFramesExist() {
        var analysis = StackTraceAnalyzer.analyze("""
                java.lang.NullPointerException: x
                \tat java.base/java.util.Objects.requireNonNull(Objects.java:233)
                \tat org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1089)""");

        assertThat(analysis.probableOrigin()).isNull();
        assertThat(analysis.note()).startsWith("Insufficient evidence");
        assertThat(analysis.chain().getFirst().frames()).allMatch(StackTraceAnalyzer.Frame::framework);
    }

    @Test
    void handlesMissingStackTrace() {
        assertThat(StackTraceAnalyzer.analyze(null).probableOrigin()).isNull();
        assertThat(StackTraceAnalyzer.analyze("  ").note()).contains("No stack trace");
    }
}
