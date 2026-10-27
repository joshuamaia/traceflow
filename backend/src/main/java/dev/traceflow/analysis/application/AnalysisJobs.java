package dev.traceflow.analysis.application;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import dev.traceflow.analysis.domain.Analysis;
import dev.traceflow.analysis.domain.AnalysisRepository;
import dev.traceflow.analysis.domain.AnalysisStatus;
import dev.traceflow.analysis.domain.SourceKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs import processing asynchronously. Clients follow progress by polling the job endpoint.
 */
@Component
public class AnalysisJobs {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobs.class);

    private final ExecutorService executor;
    private final IngestionPipeline pipeline;
    private final AnalysisRepository analyses;

    public AnalysisJobs(@Qualifier("ingestionExecutor") ExecutorService executor, IngestionPipeline pipeline,
                        AnalysisRepository analyses) {
        this.executor = executor;
        this.pipeline = pipeline;
        this.analyses = analyses;
    }

    public void submit(UUID analysisId) {
        executor.submit(() -> pipeline.process(analysisId));
    }

    /** Resumes imports interrupted by a restart. */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void resumeInterrupted() {
        List<Analysis> pending = analyses.findByStatusIn(List.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING));
        for (Analysis analysis : pending) {
            if (analysis.getKind() == SourceKind.IMPORT) {
                log.info("Resuming interrupted analysis {}", analysis.getId());
                submit(analysis.getId());
            }
        }
    }
}
