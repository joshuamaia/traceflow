package dev.traceflow.analysis.application;

import java.time.Duration;
import java.time.Instant;

import dev.traceflow.analysis.domain.Analysis;
import dev.traceflow.analysis.domain.AnalysisRepository;
import dev.traceflow.analysis.domain.AnalysisStatus;
import dev.traceflow.analysis.infrastructure.FileStorage;
import dev.traceflow.analysis.infrastructure.IngestionStore;
import dev.traceflow.config.TraceflowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes analyses (events, traces and stored files) older than the configured retention period. */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final AnalysisRepository analyses;
    private final IngestionStore store;
    private final FileStorage storage;
    private final TraceflowProperties properties;

    public RetentionJob(AnalysisRepository analyses, IngestionStore store, FileStorage storage,
                        TraceflowProperties properties) {
        this.analyses = analyses;
        this.store = store;
        this.storage = storage;
        this.properties = properties;
    }

    @Scheduled(cron = "${traceflow.retention.cron:0 0 3 * * *}")
    public void scheduledPurge() {
        if (properties.retention().enabled()) {
            purge();
        }
    }

    public int purge() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.retention().days()));
        int deleted = 0;
        for (Analysis analysis : analyses.findByCreatedAtBefore(cutoff)) {
            if (analysis.getStatus() == AnalysisStatus.PROCESSING) {
                continue;
            }
            store.deleteAnalysis(analysis.getId());
            storage.deleteAnalysis(analysis.getId());
            deleted++;
        }
        if (deleted > 0) {
            log.info("Retention policy removed {} analyses older than {} days", deleted, properties.retention().days());
        }
        return deleted;
    }
}
