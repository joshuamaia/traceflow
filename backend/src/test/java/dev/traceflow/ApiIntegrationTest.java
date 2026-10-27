package dev.traceflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end API tests against the real Spring context, Flyway schema and ingestion pipeline (H2 in PostgreSQL mode).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private JsonNode json(String body) {
        return mapper.readTree(body);
    }

    private JsonNode getJson(String url) throws Exception {
        return json(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode execute(String key, int expectedHttpStatus) throws Exception {
        String body = mvc.perform(post("/api/scenarios/{key}/execute", key))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.httpStatus").value(expectedHttpStatus))
                .andExpect(jsonPath("$.correlationId").value(startsWith("corr-")))
                .andReturn().getResponse().getContentAsString();
        JsonNode run = json(body);
        assertThat(run.get("traceRef").isNull()).as("scenario %s produced a trace", key).isFalse();
        return run;
    }

    private static List<String> edges(JsonNode detail) {
        return detail.get("edges").valueStream()
                .map(e -> e.get("source").asString() + "->" + e.get("target").asString() + ":" + e.get("relation").asString()
                        + ":" + e.get("mode").asString())
                .toList();
    }

    @Test
    void catalogListsTheFiveScenarios() throws Exception {
        mvc.perform(get("/api/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[*].key").value(hasItem("circuit-breaker")));
    }

    @Test
    void successfulRequestProducesAConfirmedFourHopTraceWithoutErrors() throws Exception {
        JsonNode run = execute("successful-request", 200);
        JsonNode detail = getJson("/api/traces/" + run.get("traceRef").asString());

        assertThat(detail.at("/trace/status").asString()).isEqualTo("OK");
        assertThat(detail.at("/trace/durationKind").asString()).isEqualTo("REPORTED");
        assertThat(detail.at("/trace/errorCount").asInt()).isZero();
        assertThat(edges(detail)).containsExactlyInAnyOrder(
                "api-gateway->order-service:CONFIRMED:SYNC",
                "order-service->payment-service:CONFIRMED:SYNC",
                "payment-service->postgres-db:CONFIRMED:SYNC");
        assertThat(detail.get("errorOrigin").isNull()).isTrue();
        assertThat(detail.get("spans").size()).isEqualTo(4);
        // a second run is deterministic in behaviour but uses new identifiers
        JsonNode second = execute("successful-request", 200);
        assertThat(second.get("correlationId").asString()).isNotEqualTo(run.get("correlationId").asString());
    }

    @Test
    void serviceTimeoutReturns504AndBlamesThePaymentService() throws Exception {
        JsonNode run = execute("service-timeout", 504);
        assertThat(run.at("/details/timeoutMs").asLong()).isEqualTo(5000);
        JsonNode detail = getJson("/api/traces/" + run.get("traceRef").asString());

        assertThat(detail.at("/trace/httpStatus").asInt()).isEqualTo(504);
        assertThat(detail.at("/trace/durationMs").asLong()).isBetween(5000L, 5100L);
        assertThat(detail.at("/errorOrigin/service").asString()).isEqualTo("payment-service");
        JsonNode payment = detail.get("spans").valueStream()
                .filter(s -> s.get("service").asString().equals("payment-service")).findFirst().orElseThrow();
        assertThat(payment.get("durationMs").asLong()).isGreaterThan(5000);
        assertThat(payment.get("status").asString()).isEqualTo("ERROR");
    }

    @Test
    void databaseExceptionExposesTheStackTraceAndProbableOrigin() throws Exception {
        JsonNode run = execute("database-exception", 500);
        String correlationId = run.get("correlationId").asString();

        JsonNode errors = getJson("/api/errors?correlationId=" + correlationId);
        JsonNode withStack = errors.get("content").valueStream()
                .filter(e -> e.get("hasStackTrace").asBoolean()).findFirst().orElseThrow();
        assertThat(withStack.get("exceptionClass").asString())
                .isEqualTo("org.springframework.dao.DataIntegrityViolationException");

        JsonNode detail = getJson("/api/errors/" + withStack.get("id").asString());
        assertThat(detail.at("/stackAnalysis/rootCause/className").asString()).isEqualTo("java.sql.SQLException");
        assertThat(detail.at("/stackAnalysis/probableOrigin/className").asString())
                .isEqualTo("dev.traceflow.scenarios.simulated.OrderRepository");
        assertThat(detail.at("/event/stackTrace").asString()).contains("Caused by: java.sql.SQLException");
        assertThat(detail.at("/trace/correlationId").asString()).isEqualTo(correlationId);

        JsonNode trace = getJson("/api/traces/" + run.get("traceRef").asString());
        assertThat(edges(trace)).contains("order-service->postgres-db:CONFIRMED:SYNC");
    }

    @Test
    void circuitBreakerRecordsRetriesStateTransitionAndRejectedCalls() throws Exception {
        JsonNode run = execute("circuit-breaker", 503);
        assertThat(run.at("/details/attempts").asInt()).isEqualTo(3);
        assertThat(run.at("/details/circuitBreakerState").asString()).isEqualTo("OPEN");
        assertThat(run.at("/details/stateTransitions/0").asString()).isEqualTo("CLOSED -> OPEN");
        assertThat(run.at("/details/rejectedCalls").asInt()).isEqualTo(2);

        JsonNode detail = getJson("/api/traces/" + run.get("traceRef").asString());
        JsonNode edge = detail.get("edges").get(0);
        assertThat(edge.get("target").asString()).isEqualTo("external-payment-api");
        assertThat(edge.get("calls").asInt()).isEqualTo(3);
        assertThat(edge.get("errors").asInt()).isEqualTo(3);
        long rejected = detail.get("timeline").valueStream()
                .filter(e -> "io.github.resilience4j.circuitbreaker.CallNotPermittedException".equals(e.get("exceptionClass").asString()))
                .count();
        assertThat(rejected).isEqualTo(2);
        assertThat(detail.get("timeline").valueStream().anyMatch(e -> e.get("message").asString().contains("CLOSED to OPEN")))
                .isTrue();
    }

    @Test
    void asyncMessagingIsRenderedAsAsynchronousFlowWithLatency() throws Exception {
        JsonNode run = execute("async-messaging", 202);
        assertThat(run.at("/details/messageId").asString()).startsWith("msg-");
        assertThat(run.at("/details/deliveryLatencyMs").asLong()).isPositive();
        assertThat(run.at("/details/consumerThread").asString()).isEqualTo("notification-consumer-1");

        JsonNode detail = getJson("/api/traces/" + run.get("traceRef").asString());
        assertThat(edges(detail)).containsExactlyInAnyOrder(
                "order-service->message-broker:CONFIRMED:ASYNC",
                "message-broker->notification-service:CONFIRMED:ASYNC");
        assertThat(detail.get("nodes").valueStream().anyMatch(n -> n.get("type").asString().equals("BROKER"))).isTrue();
        mvc.perform(get("/api/traces/by-correlation/{id}", run.get("correlationId").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void importsDistributedLogsCorrelatingAcrossFilesAndReportsParseErrors() throws Exception {
        var text = new MockMultipartFile("files", "order-service.log", "text/plain", """
                2026-10-27 10:15:30 INFO [order-service] [correlationId=imp-1] Starting order processing
                2026-10-27 10:15:30.100 INFO [order-service] [correlationId=imp-1] Calling payment-service password=hunter2
                garbage line
                2026-10-27 10:15:31 ERROR [order-service] [correlationId=imp-1] Payment failed
                java.lang.IllegalStateException: declined
                \tat com.acme.PaymentClient.charge(PaymentClient.java:42)
                """.getBytes(StandardCharsets.UTF_8));
        var jsonl = new MockMultipartFile("files", "payment-service.jsonl", "application/json", """
                {"@timestamp":"2026-10-27T10:15:30.200Z","level":"INFO","correlationId":"imp-1","message":"Charging"}
                {"@timestamp":"2026-10-27T10:15:30.900Z","level":"ERROR","correlationId":"imp-1","message":"Declined"}
                """.getBytes(StandardCharsets.UTF_8));

        String body = mvc.perform(multipart("/api/imports").file(text).file(jsonl))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.files.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String analysisId = json(body).get("id").asString();
        JsonNode job = awaitJob(analysisId);
        assertThat(job.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(job.get("eventsCount").asLong()).isEqualTo(5);
        assertThat(job.get("parseErrorsCount").asLong()).isEqualTo(1);
        assertThat(job.get("tracesCount").asLong()).isEqualTo(1);

        JsonNode traces = getJson("/api/traces?analysisId=" + analysisId);
        JsonNode trace = traces.at("/content/0");
        assertThat(trace.get("services").valueStream().map(JsonNode::asString).toList())
                .containsExactly("order-service", "payment-service");
        assertThat(trace.get("durationKind").asString()).isEqualTo("OBSERVED");
        JsonNode detail = getJson("/api/traces/" + trace.get("id").asString());
        assertThat(edges(detail)).containsExactly("order-service->payment-service:INFERRED:SYNC");

        // secrets are masked in stored lines and parsed events
        JsonNode analysis = getJson("/api/analyses/" + analysisId);
        String fileId = analysis.at("/files/0/id").asString();
        JsonNode lines = getJson("/api/analyses/" + analysisId + "/files/" + fileId + "/lines?from=2&limit=1");
        assertThat(lines.at("/lines/0/text").asString()).contains("password=[MASKED]").doesNotContain("hunter2");
        mvc.perform(get("/api/analyses/{id}/parse-errors", analysisId))
                .andExpect(jsonPath("$.content[0].lineNumber").value(3));

        // filters
        mvc.perform(get("/api/events").param("analysisId", analysisId).param("level", "ERROR"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/events").param("analysisId", analysisId).param("q", "charging"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/events").param("scope", "DEMO").param("analysisId", analysisId))
                .andExpect(jsonPath("$.totalElements").value(0));
        var export = mvc.perform(get("/api/events/export.csv").param("analysisId", analysisId))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(export))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("timestamp,level,service")));
        mvc.perform(get("/api/search").param("q", "imp-1"))
                .andExpect(jsonPath("$.traces[0].correlationId").value("imp-1"));

        // reprocess then delete
        mvc.perform(post("/api/analyses/{id}/reprocess", analysisId)).andExpect(status().isAccepted());
        assertThat(awaitJob(analysisId).get("eventsCount").asLong()).isEqualTo(5);
        mvc.perform(delete("/api/analyses/{id}", analysisId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/analyses/{id}", analysisId)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(startsWith("Analysis")));
        mvc.perform(get("/api/events").param("analysisId", analysisId)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsInvalidUploads() throws Exception {
        mvc.perform(multipart("/api/imports").file(new MockMultipartFile("files", "../../etc/passwd.exe", "text/plain", "x".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("File 'passwd.exe' has an unsupported extension")));
        mvc.perform(multipart("/api/imports").file(new MockMultipartFile("files", "empty.log", "text/plain", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File 'empty.log' is empty"));
        mvc.perform(multipart("/api/imports").file(new MockMultipartFile("files", "a.json", "application/json", "hello".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("File 'a.json' is not valid JSON")));
        mvc.perform(multipart("/api/imports").file(new MockMultipartFile("files", "bin.log", "text/plain", new byte[]{'a', 0, 'b'})))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dashboardAnalyticsAndErrorGroupsUseRealData() throws Exception {
        execute("database-exception", 500);
        execute("circuit-breaker", 503);

        mvc.perform(get("/api/dashboard").param("scope", "DEMO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTraces").value(greaterThan(1)))
                .andExpect(jsonPath("$.eventsOverTime.buckets.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.avgDurationBasis").value("REPORTED"));
        mvc.perform(get("/api/analytics").param("scope", "DEMO"))
                .andExpect(jsonPath("$.hasData").value(true))
                .andExpect(jsonPath("$.requestDurationBasis").value("REPORTED"))
                .andExpect(jsonPath("$.operationDurations.length()").value(greaterThan(0)));
        mvc.perform(get("/api/analytics").param("scope", "DEMO").param("from", "2000-01-01T00:00:00Z")
                        .param("to", "2000-01-02T00:00:00Z"))
                .andExpect(jsonPath("$.hasData").value(false))
                .andExpect(jsonPath("$.notes[0]").value(startsWith("No events found")));
        mvc.perform(get("/api/errors/groups").param("scope", "DEMO").param("exceptionClass",
                        "io.github.resilience4j.circuitbreaker.CallNotPermittedException"))
                .andExpect(jsonPath("$.content[0].occurrences").value(greaterThan(1)));
        mvc.perform(get("/api/errors/facets").param("scope", "DEMO"))
                .andExpect(jsonPath("$.services").value(hasItem("order-service")));
    }

    @Test
    void liveAnalyzerParsesWithoutPersisting() throws Exception {
        String payload = mapper.writeValueAsString(Map.of("content",
                "2026-10-27 10:15:30 INFO [svc-a] [correlationId=live-1] hello token=abc\nnot a log line"));
        mvc.perform(post("/api/analyzer/parse").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(1))
                .andExpect(jsonPath("$.events[0].correlationId").value("live-1"))
                .andExpect(jsonPath("$.events[0].message").value("hello token=[MASKED]"))
                .andExpect(jsonPath("$.problems[0].lineNumber").value(2));
        mvc.perform(get("/api/traces/by-correlation/live-1")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/analyzer/parse").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("content"));
    }

    @Test
    void unknownResourcesReturnStructuredErrors() throws Exception {
        mvc.perform(post("/api/scenarios/does-not-exist/execute")).andExpect(status().isNotFound());
        mvc.perform(get("/api/traces/not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/imports']").exists());
    }

    private JsonNode awaitJob(String analysisId) throws Exception {
        JsonNode job = null;
        for (int i = 0; i < 100; i++) {
            job = getJson("/api/analyses/" + analysisId + "/job");
            if (job.get("finished").asBoolean()) {
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Job did not finish: " + job);
    }
}
