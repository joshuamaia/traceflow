package dev.traceflow.traces.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Summary of a group of correlated events (same correlationId and/or traceId inside an analysis).
 */
@Entity
@Table(name = "traces")
@Getter
@Setter
@NoArgsConstructor
public class Trace {

    @Id
    private UUID id;
    private UUID analysisId;
    private String sourceKind;
    private String correlationKey;
    private String correlationId;
    private String traceId;
    private String rootService;
    private String rootOperation;
    private Instant startedAt;
    private Instant endedAt;
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    private DurationKind durationKind;

    @Enumerated(EnumType.STRING)
    private TraceStatus status;

    private Integer httpStatus;
    private int eventCount;
    private int errorCount;
    private int warnCount;
    private int serviceCount;
    @Column(length = 2000)
    private String services;
    private Instant createdAt;

    public List<String> serviceList() {
        if (services == null || services.isBlank()) {
            return List.of();
        }
        return Arrays.asList(services.split(","));
    }
}
