package dev.traceflow.traces.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TraceRepository extends JpaRepository<Trace, UUID>, JpaSpecificationExecutor<Trace> {

    List<Trace> findByCorrelationIdOrderByStartedAtDesc(String correlationId);

    List<Trace> findByTraceIdOrderByStartedAtDesc(String traceId);

    Optional<Trace> findFirstByAnalysisIdAndCorrelationId(UUID analysisId, String correlationId);

    List<Trace> findTop10ByCorrelationIdStartingWithOrTraceIdStartingWithOrderByStartedAtDesc(String correlationId,
                                                                                               String traceId);
}
