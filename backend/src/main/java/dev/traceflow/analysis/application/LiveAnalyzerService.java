package dev.traceflow.analysis.application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.traceflow.common.security.SensitiveDataMasker;
import dev.traceflow.common.web.BadRequestException;
import dev.traceflow.config.TraceflowProperties;
import dev.traceflow.parsing.LogStreamParser;
import dev.traceflow.parsing.ParseProblem;
import dev.traceflow.parsing.ParsedEvent;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses pasted content without persisting anything. Useful for quick inspection and for public deployments where
 * persistent imports are disabled.
 */
@Service
public class LiveAnalyzerService {

    public static final int MAX_EVENTS = 5000;

    public record LiveEventDto(int index, int lineStart, int lineEnd, Instant timestamp, String timestampRaw,
                               String level, String service, String thread, String logger, String correlationId,
                               String traceId, String spanId, String parentSpanId, String spanKind, String operation,
                               String peerService, String message, String exceptionClass, String exceptionMessage,
                               String stackTrace, Long durationMs, Integer httpStatus, boolean error, String parser,
                               Map<String, Object> attributes) {
    }

    public record LiveProblemDto(int lineNumber, String severity, String reason) {
    }

    public record LiveAnalysisDto(List<String> lines, List<LiveEventDto> events, List<LiveProblemDto> problems,
                                  int totalLines, int totalEvents, boolean truncated, String detectedFormat,
                                  Map<String, Long> levels, List<String> services, List<String> correlationIds) {
    }

    private final LogStreamParser parser;
    private final TraceflowProperties properties;

    public LiveAnalyzerService(ObjectMapper mapper, TraceflowProperties properties) {
        this.parser = new LogStreamParser(mapper);
        this.properties = properties;
    }

    public LiveAnalysisDto analyze(String content, String fileName) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Content is empty");
        }
        long max = properties.imports().maxPasteSize().toBytes();
        if (content.getBytes(StandardCharsets.UTF_8).length > max) {
            throw new BadRequestException("Content exceeds the maximum of " + (max / 1024) + " KB for live analysis");
        }
        if (content.indexOf('\u0000') >= 0) {
            throw new BadRequestException("Binary content is not supported");
        }
        String masked = String.join("\n", content.lines().map(SensitiveDataMasker::mask).toList());
        List<LiveEventDto> events = new ArrayList<>();
        List<LiveProblemDto> problems = new ArrayList<>();
        Map<String, Long> levels = new LinkedHashMap<>();
        Set<String> services = new LinkedHashSet<>();
        Set<String> correlations = new LinkedHashSet<>();
        int[] total = {0};
        LogStreamParser.Sink sink = new LogStreamParser.Sink() {
            @Override
            public void event(ParsedEvent e) {
                total[0]++;
                levels.merge(e.getLevel(), 1L, Long::sum);
                if (e.getService() != null) {
                    services.add(e.getService());
                }
                if (e.getCorrelationId() != null && correlations.size() < 200) {
                    correlations.add(e.getCorrelationId());
                }
                if (events.size() < MAX_EVENTS) {
                    events.add(new LiveEventDto(events.size(), e.getLineStart(), e.getLineEnd(), e.getTimestamp(),
                            e.getTimestampRaw(), e.getLevel(), e.getService(), e.getThread(), e.getLogger(),
                            e.getCorrelationId(), e.getTraceId(), e.getSpanId(), e.getParentSpanId(), e.getSpanKind(),
                            e.getOperation(), e.getPeerService(), e.getMessage(), e.getExceptionClass(),
                            e.getExceptionMessage(), e.stackTraceText(), e.getDurationMs(), e.getHttpStatus(),
                            e.isError(), e.getParser(), e.getAttributes()));
                }
            }

            @Override
            public void problem(ParseProblem p) {
                if (problems.size() < 1000) {
                    problems.add(new LiveProblemDto(p.lineNumber(), p.severity().name(), p.reason()));
                }
            }
        };
        var options = new LogStreamParser.Options(properties.parsing().defaultZone(),
                IngestionPipeline.serviceFromFileName(fileName), null);
        LogStreamParser.Stats stats;
        String trimmed = masked.stripLeading();
        try {
            if (trimmed.startsWith("[")) {
                stats = parser.parseJsonArray(new StringReader(masked), options, sink);
            } else {
                stats = parser.parseLines(new BufferedReader(new StringReader(masked)), options, sink);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> lines = masked.lines().limit(20000).toList();
        return new LiveAnalysisDto(lines, events, problems, (int) masked.lines().count(), total[0],
                total[0] > events.size(), stats.detectedFormat(), levels, List.copyOf(services),
                List.copyOf(correlations));
    }
}
