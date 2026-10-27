package dev.traceflow.analysis.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "analysis_files")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisFile {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id")
    private Analysis analysis;

    @Column(nullable = false)
    private String originalName;

    /** Server generated file name (UUID based). User supplied names are never used to build paths. */
    @Column(nullable = false)
    private String storedName;

    @Column(nullable = false)
    private String extension;

    private long sizeBytes;
    private long lineCount;
    private long eventsCount;
    private long parseErrorsCount;
    private String detectedFormat;

    @Column(nullable = false)
    private Instant createdAt;

    public static AnalysisFile create(UUID id, String originalName, String storedName, String extension,
                                      long sizeBytes) {
        var file = new AnalysisFile();
        file.id = id;
        file.originalName = originalName;
        file.storedName = storedName;
        file.extension = extension;
        file.sizeBytes = sizeBytes;
        file.createdAt = Instant.now();
        return file;
    }
}
