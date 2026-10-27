package dev.traceflow.errors.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.traceflow.events.application.EventDto;
import dev.traceflow.parsing.StackTraceAnalyzer;
import dev.traceflow.traces.application.TraceDtos.TraceSummaryDto;

public final class ErrorDtos {

    private ErrorDtos() {
    }

    /** Similar errors grouped by fingerprint. */
    public record ErrorGroupDto(String fingerprint, String exceptionClass, String sampleMessage, long occurrences,
                                Instant firstSeen, Instant lastSeen, List<String> services, UUID sampleEventId,
                                UUID sampleTraceRef, String sampleCorrelationId) {
    }

    public record ErrorDetailDto(EventDto event, StackTraceAnalyzer.Analysis stackAnalysis, TraceSummaryDto trace,
                                 long similarOccurrences) {
    }

    public record ErrorFacetsDto(List<String> services, List<String> exceptionClasses, long totalErrors) {
    }
}
