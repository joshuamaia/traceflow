package dev.traceflow.events.domain;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParseErrorRepository extends JpaRepository<ParseErrorRecord, UUID> {

    Page<ParseErrorRecord> findByAnalysisId(UUID analysisId, Pageable pageable);

    Page<ParseErrorRecord> findByAnalysisIdAndFileId(UUID analysisId, UUID fileId, Pageable pageable);
}
