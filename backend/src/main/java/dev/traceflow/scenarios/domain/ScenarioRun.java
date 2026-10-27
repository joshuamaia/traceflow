package dev.traceflow.scenarios.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "scenario_runs")
@Getter
@Setter
@NoArgsConstructor
public class ScenarioRun {

    public enum Status {
        RUNNING, COMPLETED, FAILED
    }

    @Id
    private UUID id;
    private String scenarioKey;

    @Enumerated(EnumType.STRING)
    private Status status;

    private UUID analysisId;
    private UUID traceRef;
    private String correlationId;
    private String traceId;
    private Integer httpStatus;
    private String outcome;
    private String details;
    private String failureReason;
    private Instant startedAt;
    private Instant finishedAt;

    public static ScenarioRun start(String scenarioKey, UUID analysisId) {
        var run = new ScenarioRun();
        run.id = UUID.randomUUID();
        run.scenarioKey = scenarioKey;
        run.analysisId = analysisId;
        run.status = Status.RUNNING;
        run.startedAt = Instant.now();
        return run;
    }
}
