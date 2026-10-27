package dev.traceflow.scenarios.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScenarioDtos {

    private ScenarioDtos() {
    }

    public record ScenarioRunDto(UUID id, String scenarioKey, String scenarioName, String status, Integer httpStatus,
                                 String outcome, String correlationId, String traceId, UUID traceRef, UUID analysisId,
                                 Instant startedAt, Instant finishedAt, Map<String, Object> details,
                                 String failureReason) {
    }

    public record ScenarioDto(String key, String name, String description, List<String> flow, String expectedOutcome,
                              long totalRuns, ScenarioRunDto lastRun) {
    }
}
