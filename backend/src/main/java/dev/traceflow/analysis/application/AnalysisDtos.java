package dev.traceflow.analysis.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.traceflow.analysis.domain.Analysis;
import dev.traceflow.analysis.domain.AnalysisFile;
import dev.traceflow.events.domain.ParseErrorRecord;

/** API representations of analyses (import batches and demo runs). */
public final class AnalysisDtos {

    private AnalysisDtos() {
    }

    public record FileDto(UUID id, String originalName, String extension, long sizeBytes, long lineCount,
                          long eventsCount, long parseErrorsCount, String detectedFormat) {
        public static FileDto of(AnalysisFile f) {
            return new FileDto(f.getId(), f.getOriginalName(), f.getExtension(), f.getSizeBytes(), f.getLineCount(),
                    f.getEventsCount(), f.getParseErrorsCount(), f.getDetectedFormat());
        }
    }

    public record AnalysisDto(UUID id, String kind, String name, String status, int progressPercent,
                              long totalBytes, long processedBytes, long linesProcessed, long eventsCount,
                              long tracesCount, long errorEventsCount, long parseErrorsCount, String failureReason,
                              Instant createdAt, Instant startedAt, Instant finishedAt, List<FileDto> files) {
        public static AnalysisDto of(Analysis a) {
            return new AnalysisDto(a.getId(), a.getKind().name(), a.getName(), a.getStatus().name(),
                    a.progressPercent(), a.getTotalBytes(), a.getProcessedBytes(), a.getLinesProcessed(),
                    a.getEventsCount(), a.getTracesCount(), a.getErrorEventsCount(), a.getParseErrorsCount(),
                    a.getFailureReason(), a.getCreatedAt(), a.getStartedAt(), a.getFinishedAt(),
                    a.getFiles().stream().map(FileDto::of).toList());
        }
    }

    public record JobDto(UUID analysisId, String status, int progressPercent, long processedBytes, long totalBytes,
                         long linesProcessed, long eventsCount, long tracesCount, long parseErrorsCount,
                         String failureReason, boolean finished) {
        public static JobDto of(Analysis a) {
            return new JobDto(a.getId(), a.getStatus().name(), a.progressPercent(), a.getProcessedBytes(),
                    a.getTotalBytes(), a.getLinesProcessed(), a.getEventsCount(), a.getTracesCount(),
                    a.getParseErrorsCount(), a.getFailureReason(), a.getStatus().isTerminal());
        }
    }

    public record LineDto(int number, String text) {
    }

    public record LinesDto(UUID fileId, int fromLine, List<LineDto> lines, boolean hasMore, long totalLines) {
    }

    public record ParseErrorDto(UUID id, UUID fileId, int lineNumber, String severity, String reason, String raw) {
        public static ParseErrorDto of(ParseErrorRecord r) {
            return new ParseErrorDto(r.getId(), r.getFileId(), r.getLineNumber(), r.getSeverity(), r.getReason(),
                    r.getRaw());
        }
    }
}
