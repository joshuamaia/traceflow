package dev.traceflow.analysis.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisFileRepository extends JpaRepository<AnalysisFile, UUID> {

    List<AnalysisFile> findByAnalysisIdOrderByCreatedAtAsc(UUID analysisId);
}
