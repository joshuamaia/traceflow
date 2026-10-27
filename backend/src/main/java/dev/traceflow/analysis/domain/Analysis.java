package dev.traceflow.analysis.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A unit of analysis: a set of files processed together (an import batch or a demo scenario run). Traces are
 * correlated across all files of the same analysis, which allows distributed logs split per service to be joined.
 */
@Entity
@Table(name = "analyses")
@Getter
@Setter
@NoArgsConstructor
public class Analysis {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceKind kind;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    private long totalBytes;
    private long processedBytes;
    private long linesProcessed;
    private long eventsCount;
    private long tracesCount;
    private long errorEventsCount;
    private long parseErrorsCount;
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<AnalysisFile> files = new ArrayList<>();

    public static Analysis create(SourceKind kind, String name) {
        var analysis = new Analysis();
        analysis.id = UUID.randomUUID();
        analysis.kind = kind;
        analysis.name = name;
        analysis.status = AnalysisStatus.PENDING;
        analysis.createdAt = Instant.now();
        return analysis;
    }

    public void addFile(AnalysisFile file) {
        file.setAnalysis(this);
        files.add(file);
        totalBytes += file.getSizeBytes();
    }

    public void resetCounters() {
        processedBytes = 0;
        linesProcessed = 0;
        eventsCount = 0;
        tracesCount = 0;
        errorEventsCount = 0;
        parseErrorsCount = 0;
        failureReason = null;
        startedAt = null;
        finishedAt = null;
    }

    public int progressPercent() {
        if (status == AnalysisStatus.COMPLETED) {
            return 100;
        }
        if (totalBytes <= 0) {
            return 0;
        }
        return (int) Math.min(99, processedBytes * 100 / totalBytes);
    }
}
