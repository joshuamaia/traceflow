package dev.traceflow.analysis.application;

import java.io.BufferedReader;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import dev.traceflow.analysis.domain.Analysis;
import dev.traceflow.analysis.domain.AnalysisFile;
import dev.traceflow.analysis.domain.AnalysisFileRepository;
import dev.traceflow.analysis.domain.AnalysisRepository;
import dev.traceflow.analysis.domain.AnalysisStatus;
import dev.traceflow.analysis.infrastructure.CountingInputStream;
import dev.traceflow.analysis.infrastructure.FileStorage;
import dev.traceflow.analysis.infrastructure.IngestionStore;
import dev.traceflow.config.TraceflowProperties;
import dev.traceflow.parsing.Fingerprints;
import dev.traceflow.parsing.LogStreamParser;
import dev.traceflow.parsing.ParseProblem;
import dev.traceflow.parsing.ParsedEvent;
import dev.traceflow.traces.application.CorrelationSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Processes the files of an analysis: streaming parse, masking-safe persistence in batches, correlation into traces.
 * The same pipeline is used for imported files and for logs produced by demo scenarios.
 */
@Service
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);
    private static final Pattern SERVICE_FILE_NAME = Pattern.compile("^[a-z][a-z0-9]*(-[a-z0-9]+)+$");

    private final AnalysisRepository analyses;
    private final AnalysisFileRepository files;
    private final FileStorage storage;
    private final IngestionStore store;
    private final ObjectMapper mapper;
    private final TraceflowProperties properties;
    private final LogStreamParser parser;

    public IngestionPipeline(AnalysisRepository analyses, AnalysisFileRepository files, FileStorage storage,
                             IngestionStore store, ObjectMapper mapper, TraceflowProperties properties) {
        this.analyses = analyses;
        this.files = files;
        this.storage = storage;
        this.store = store;
        this.mapper = mapper;
        this.properties = properties;
        this.parser = new LogStreamParser(mapper);
    }

    /** Runs synchronously; callers decide whether to run it on a background executor. */
    public void process(UUID analysisId) {
        Analysis analysis = analyses.findById(analysisId).orElse(null);
        if (analysis == null) {
            log.warn("Analysis {} no longer exists, skipping processing", analysisId);
            return;
        }
        analysis.resetCounters();
        analysis.setStatus(AnalysisStatus.PROCESSING);
        analysis.setStartedAt(Instant.now());
        analyses.save(analysis);
        log.info("Processing analysis {} ({}, {} bytes)", analysisId, analysis.getName(), analysis.getTotalBytes());
        try {
            store.deleteDerivedData(analysisId);
            var session = new CorrelationSession();
            var run = new RunState(analysis);
            for (AnalysisFile file : files.findByAnalysisIdOrderByCreatedAtAsc(analysisId)) {
                processFile(analysis, file, session, run);
                run.bytesBefore += file.getSizeBytes();
            }
            var traces = session.buildTraces(analysisId, analysis.getKind().name());
            store.insertTraces(traces);
            store.applyMerges(session.merges());

            Analysis done = analyses.findById(analysisId).orElse(null);
            if (done == null) {
                return;
            }
            done.setProcessedBytes(done.getTotalBytes());
            done.setLinesProcessed(run.lines);
            done.setEventsCount(run.events);
            done.setParseErrorsCount(run.problems);
            done.setTracesCount(traces.size());
            done.setErrorEventsCount(store.countErrorEvents(analysisId));
            done.setStatus(AnalysisStatus.COMPLETED);
            done.setFinishedAt(Instant.now());
            analyses.save(done);
            log.info("Analysis {} completed: {} events, {} traces, {} parse problems", analysisId, run.events,
                    traces.size(), run.problems);
        } catch (Exception e) {
            log.error("Analysis {} failed", analysisId, e);
            analyses.findById(analysisId).ifPresent(failed -> {
                failed.setStatus(AnalysisStatus.FAILED);
                failed.setFailureReason(safeReason(e));
                failed.setFinishedAt(Instant.now());
                analyses.save(failed);
            });
        }
    }

    private void processFile(Analysis analysis, AnalysisFile file, CorrelationSession session, RunState run)
            throws Exception {
        var options = new LogStreamParser.Options(properties.parsing().defaultZone(),
                serviceFromFileName(file.getOriginalName()), null);
        var sink = new BatchSink(analysis, file, session, run);
        LogStreamParser.Stats stats;
        try (InputStream raw = storage.open(analysis.getId(), file.getStoredName());
             CountingInputStream counting = new CountingInputStream(raw);
             BufferedReader reader = FileStorage.reader(counting)) {
            sink.counter = counting;
            reader.mark(64);
            int first = skipWhitespace(reader);
            reader.reset();
            if (first == '[' && file.getExtension().equals("json")) {
                stats = parser.parseJsonArray(reader, options, sink);
            } else {
                stats = parser.parseLines(reader, options, sink);
            }
            sink.flush();
        }
        run.lines += stats.lines();
        AnalysisFile managed = files.findById(file.getId()).orElse(file);
        managed.setLineCount(stats.lines());
        managed.setEventsCount(stats.events());
        managed.setParseErrorsCount(stats.problems());
        managed.setDetectedFormat(stats.detectedFormat());
        files.save(managed);
    }

    private static int skipWhitespace(BufferedReader reader) throws java.io.IOException {
        int c;
        int read = 0;
        while ((c = reader.read()) != -1 && read++ < 60) {
            if (!Character.isWhitespace(c) && c != '﻿') {
                return c;
            }
        }
        return c;
    }

    /** Uses kebab-case file names such as {@code payment-service.log} as service name fallback. */
    static String serviceFromFileName(String originalName) {
        if (originalName == null) {
            return null;
        }
        String base = originalName.toLowerCase();
        int dot = base.indexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return SERVICE_FILE_NAME.matcher(base).matches() && base.length() <= 64 ? base : null;
    }

    private static String safeReason(Exception e) {
        String type = e.getClass().getSimpleName();
        return switch (e) {
            case java.io.IOException io -> "I/O error while reading the file (" + type + ")";
            case org.springframework.dao.DataAccessException dae -> "Database error while storing events (" + type + ")";
            default -> "Processing failed (" + type + ")";
        };
    }

    private static final class RunState {
        final Analysis analysis;
        long seq;
        long events;
        long problems;
        long lines;
        long bytesBefore;

        RunState(Analysis analysis) {
            this.analysis = analysis;
        }
    }

    private final class BatchSink implements LogStreamParser.Sink {
        private final Analysis analysis;
        private final AnalysisFile file;
        private final CorrelationSession session;
        private final RunState run;
        private final int batchSize = properties.imports().batchSize();
        private final List<IngestionStore.EventRow> events = new ArrayList<>();
        private final List<IngestionStore.ProblemRow> problems = new ArrayList<>();
        private CountingInputStream counter;
        private int lastLine;

        BatchSink(Analysis analysis, AnalysisFile file, CorrelationSession session, RunState run) {
            this.analysis = analysis;
            this.file = file;
            this.session = session;
            this.run = run;
        }

        @Override
        public void event(ParsedEvent e) {
            long seq = ++run.seq;
            var assignment = session.assign(e, seq);
            events.add(toRow(e, seq, assignment));
            run.events++;
            lastLine = Math.max(lastLine, e.getLineEnd());
            if (events.size() >= batchSize) {
                flush();
            }
        }

        @Override
        public void problem(ParseProblem p) {
            problems.add(new IngestionStore.ProblemRow(UUID.randomUUID(), analysis.getId(), file.getId(),
                    p.lineNumber(), p.severity().name(), cut(p.reason(), 500), cut(p.raw(), 4000)));
            run.problems++;
            if (problems.size() >= batchSize) {
                flush();
            }
        }

        void flush() {
            store.insertEvents(events);
            store.insertProblems(problems);
            events.clear();
            problems.clear();
            long processed = run.bytesBefore + (counter == null ? 0 : Math.min(counter.count(), file.getSizeBytes()));
            analyses.updateProgress(analysis.getId(), processed, run.lines + lastLine, run.events, run.problems);
        }

        private IngestionStore.EventRow toRow(ParsedEvent e, long seq, CorrelationSession.Assignment assignment) {
            String stack = e.stackTraceText();
            boolean error = e.isError();
            String fingerprint = error || e.getExceptionClass() != null
                    ? Fingerprints.of(e.getExceptionClass(), e.getExceptionClass() != null
                    ? e.getExceptionMessage() : e.getMessage(), stack)
                    : null;
            Map<String, Object> attributes = e.getAttributes();
            if (e.isServiceFromFileName()) {
                attributes.put("_serviceSource", "fileName");
            }
            String attributesJson = attributes.isEmpty() ? null : cut(mapper.writeValueAsString(attributes), 20000);
            return new IngestionStore.EventRow(UUID.randomUUID(), analysis.getId(), file.getId(),
                    analysis.getKind().name(), assignment == null ? null : assignment.traceRef(),
                    assignment == null ? null : assignment.correlationKey(), seq, e.getLineStart(), e.getLineEnd(),
                    e.getTimestamp(), cut(e.getTimestampRaw(), 64), e.getLevel(), cut(e.getService(), 128),
                    cut(e.getThread(), 128), cut(e.getLogger(), 255), cut(e.getCorrelationId(), 128),
                    cut(e.getTraceId(), 64), cut(e.getSpanId(), 64), cut(e.getParentSpanId(), 64),
                    cut(e.getSpanKind(), 16), cut(e.getOperation(), 255), cut(e.getPeerService(), 128),
                    cut(e.getPeerType(), 32), cut(e.getMessageId(), 128), cut(e.getMessage(), 20000),
                    cut(e.getExceptionClass(), 255), cut(e.getExceptionMessage(), 4000), cut(stack, 100000),
                    e.getDurationMs(), e.getHttpStatus(), error, fingerprint, attributesJson,
                    cut(e.rawText(), 120000), e.getParser());
        }
    }

    static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
