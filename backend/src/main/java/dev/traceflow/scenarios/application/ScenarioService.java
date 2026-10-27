package dev.traceflow.scenarios.application;

import java.io.BufferedWriter;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.traceflow.analysis.application.IngestionPipeline;
import dev.traceflow.analysis.domain.Analysis;
import dev.traceflow.analysis.domain.AnalysisFile;
import dev.traceflow.analysis.domain.AnalysisRepository;
import dev.traceflow.analysis.domain.AnalysisStatus;
import dev.traceflow.analysis.domain.SourceKind;
import dev.traceflow.analysis.infrastructure.FileStorage;
import dev.traceflow.analysis.infrastructure.IngestionStore;
import dev.traceflow.common.web.NotFoundException;
import dev.traceflow.common.web.PageResponse;
import dev.traceflow.common.web.Paging;
import dev.traceflow.config.TraceflowProperties;
import dev.traceflow.scenarios.application.ScenarioDtos.ScenarioDto;
import dev.traceflow.scenarios.application.ScenarioDtos.ScenarioRunDto;
import dev.traceflow.scenarios.domain.ScenarioRun;
import dev.traceflow.scenarios.domain.ScenarioRunRepository;
import dev.traceflow.scenarios.engine.DemoScenario;
import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.ScenarioLog;
import dev.traceflow.scenarios.engine.ScenarioResult;
import dev.traceflow.scenarios.engine.SimulatedClock;
import dev.traceflow.traces.domain.TraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Executes demo scenarios. Each run writes its structured logs to a file that is processed by the regular
 * {@link IngestionPipeline}, so demo data goes through exactly the same parsing and correlation as imported logs.
 */
@Service
public class ScenarioService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioService.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final Map<String, DemoScenario> scenarios;
    private final AnalysisRepository analyses;
    private final ScenarioRunRepository runs;
    private final TraceRepository traces;
    private final FileStorage storage;
    private final IngestionStore store;
    private final IngestionPipeline pipeline;
    private final ObjectMapper mapper;
    private final TraceflowProperties properties;

    public ScenarioService(List<DemoScenario> scenarios, AnalysisRepository analyses, ScenarioRunRepository runs,
                           TraceRepository traces, FileStorage storage, IngestionStore store, IngestionPipeline pipeline,
                           ObjectMapper mapper, TraceflowProperties properties) {
        this.scenarios = scenarios.stream()
                .sorted(Comparator.comparingInt(DemoScenario::order))
                .collect(Collectors.toMap(DemoScenario::key, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        this.analyses = analyses;
        this.runs = runs;
        this.traces = traces;
        this.storage = storage;
        this.store = store;
        this.pipeline = pipeline;
        this.mapper = mapper;
        this.properties = properties;
    }

    public List<ScenarioDto> catalog() {
        return scenarios.values().stream()
                .map(s -> new ScenarioDto(s.key(), s.name(), s.description(), s.flow(), s.expectedOutcome(),
                        runs.countByScenarioKey(s.key()),
                        runs.findFirstByScenarioKeyOrderByStartedAtDesc(s.key()).map(this::toDto).orElse(null)))
                .toList();
    }

    public ScenarioRunDto execute(String key) {
        DemoScenario scenario = scenario(key);
        Analysis analysis = Analysis.create(SourceKind.DEMO, "Demo: " + scenario.name());
        analyses.save(analysis);
        ScenarioRun run = runs.save(ScenarioRun.start(key, analysis.getId()));
        UUID fileId = UUID.randomUUID();
        String storedName = fileId + ".jsonl";
        try {
            ScenarioContext ctx;
            ScenarioResult result;
            try (BufferedWriter writer = storage.newWriter(analysis.getId(), storedName)) {
                ctx = new ScenarioContext(new SimulatedClock(Instant.now()), new ScenarioLog(writer, mapper));
                result = scenario.execute(ctx);
            }
            // "analysis" is the instance created above (plain file list), so the new file is cascaded on merge
            analysis.addFile(AnalysisFile.create(fileId, key + ".jsonl", storedName, "jsonl",
                    storage.size(analysis.getId(), storedName)));
            analyses.save(analysis);

            pipeline.process(analysis.getId());
            Analysis processed = analyses.findById(analysis.getId()).orElseThrow();
            if (processed.getStatus() != AnalysisStatus.COMPLETED) {
                throw new IllegalStateException("Ingestion of the scenario logs failed: " + processed.getFailureReason());
            }
            var trace = traces.findFirstByAnalysisIdAndCorrelationId(analysis.getId(), ctx.correlationId());
            run.setStatus(ScenarioRun.Status.COMPLETED);
            run.setHttpStatus(result.httpStatus());
            run.setOutcome(result.outcome());
            run.setCorrelationId(ctx.correlationId());
            run.setTraceId(ctx.traceId());
            run.setTraceRef(trace.map(t -> t.getId()).orElse(null));
            Map<String, Object> details = new LinkedHashMap<>(ctx.details());
            details.put("requestDurationMs", result.requestDuration());
            details.put("logLines", ctx.log().lines());
            details.put("eventsParsed", processed.getEventsCount());
            details.put("parseProblems", processed.getParseErrorsCount());
            run.setDetails(mapper.writeValueAsString(details));
        } catch (Exception e) {
            log.error("Scenario {} failed", key, e);
            run.setStatus(ScenarioRun.Status.FAILED);
            run.setFailureReason(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        run.setFinishedAt(Instant.now());
        runs.save(run);
        enforceRunLimit();
        return toDto(run);
    }

    public PageResponse<ScenarioRunDto> history(String key, int page, int size) {
        var pageable = Paging.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = key == null || key.isBlank() ? runs.findAll(pageable) : runs.findByScenarioKey(scenario(key).key(), pageable);
        return PageResponse.of(result, this::toDto);
    }

    public ScenarioRunDto run(UUID id) {
        return runs.findById(id).map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Scenario run " + id + " not found"));
    }

    /** Runs every scenario once, used to provide data immediately after the first start. */
    public void seedIfEmpty() {
        if (analyses.countByKind(SourceKind.DEMO) > 0) {
            return;
        }
        log.info("Seeding demo data: executing {} scenarios", scenarios.size());
        scenarios.keySet().forEach(this::execute);
    }

    private void enforceRunLimit() {
        long demoAnalyses = analyses.countByKind(SourceKind.DEMO);
        int max = properties.demo().maxRunsKept();
        if (demoAnalyses <= max) {
            return;
        }
        var oldest = analyses.findByKind(SourceKind.DEMO,
                PageRequest.of(0, (int) (demoAnalyses - max), Sort.by(Sort.Direction.ASC, "createdAt")));
        for (Analysis analysis : oldest) {
            store.deleteAnalysis(analysis.getId());
            storage.deleteAnalysis(analysis.getId());
        }
    }

    private DemoScenario scenario(String key) {
        DemoScenario scenario = scenarios.get(key);
        if (scenario == null) {
            throw new NotFoundException("Unknown scenario '" + key + "'");
        }
        return scenario;
    }

    private ScenarioRunDto toDto(ScenarioRun run) {
        DemoScenario scenario = scenarios.get(run.getScenarioKey());
        Map<String, Object> details = Map.of();
        if (run.getDetails() != null) {
            try {
                details = mapper.readValue(run.getDetails(), MAP);
            } catch (JacksonException ignored) {
                // keep empty details
            }
        }
        return new ScenarioRunDto(run.getId(), run.getScenarioKey(), scenario == null ? run.getScenarioKey()
                : scenario.name(), run.getStatus().name(), run.getHttpStatus(), run.getOutcome(), run.getCorrelationId(),
                run.getTraceId(), run.getTraceRef(), run.getAnalysisId(), run.getStartedAt(), run.getFinishedAt(),
                details, run.getFailureReason());
    }
}
