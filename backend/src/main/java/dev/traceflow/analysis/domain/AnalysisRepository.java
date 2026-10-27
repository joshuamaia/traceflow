package dev.traceflow.analysis.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    Page<Analysis> findByKind(SourceKind kind, Pageable pageable);

    List<Analysis> findByCreatedAtBefore(Instant cutoff);

    List<Analysis> findByStatusIn(List<AnalysisStatus> statuses);

    long countByKind(SourceKind kind);

    @Modifying
    @Transactional
    @Query("""
            update Analysis a set a.processedBytes = :processedBytes, a.linesProcessed = :lines,
                   a.eventsCount = :events, a.parseErrorsCount = :parseErrors
            where a.id = :id""")
    void updateProgress(UUID id, long processedBytes, long lines, long events, long parseErrors);
}
