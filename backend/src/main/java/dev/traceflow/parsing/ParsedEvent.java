package dev.traceflow.parsing;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Mutable, parser-agnostic representation of one log event while it is being assembled. Multi-line content (stack
 * traces) is appended by the {@link LogStreamParser} until the next event starts.
 */
@Getter
@Setter
public final class ParsedEvent {

    private int lineStart;
    private int lineEnd;
    private Instant timestamp;
    private String timestampRaw;
    private boolean timestampInvalid;
    private String level = LogLevels.UNKNOWN;
    private String service;
    private boolean serviceFromFileName;
    private String thread;
    private String logger;
    private String correlationId;
    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String spanKind;
    private String operation;
    private String peerService;
    private String peerType;
    private String messageId;
    private String message;
    private String exceptionClass;
    private String exceptionMessage;
    private final StringBuilder stackTrace = new StringBuilder();
    private Long durationMs;
    private Integer httpStatus;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final StringBuilder raw = new StringBuilder();
    private String parser;

    public ParsedEvent(String parser, int lineNumber, String rawLine) {
        this.parser = parser;
        this.lineStart = lineNumber;
        this.lineEnd = lineNumber;
        this.raw.append(rawLine);
    }

    /** Appends a stack trace line belonging to this event. */
    public void appendStackLine(String line, int lineNumber) {
        raw.append('\n').append(line);
        lineEnd = Math.max(lineEnd, lineNumber);
        if (!stackTrace.isEmpty()) {
            stackTrace.append('\n');
        }
        stackTrace.append(line);
        if (exceptionClass == null) {
            StackTraces.parseHeader(line).ifPresent(header -> {
                exceptionClass = header.className();
                exceptionMessage = header.message();
            });
        }
    }

    /** Sets a full stack trace (e.g. from a JSON field) and derives the exception class when possible. */
    public void setStackTraceText(String text) {
        stackTrace.setLength(0);
        if (text == null || text.isBlank()) {
            return;
        }
        stackTrace.append(text.strip());
        if (exceptionClass == null) {
            StackTraces.parseHeader(text.strip().lines().findFirst().orElse("")).ifPresent(header -> {
                exceptionClass = header.className();
                exceptionMessage = header.message();
            });
        }
    }

    public String stackTraceText() {
        return stackTrace.isEmpty() ? null : stackTrace.toString();
    }

    public String rawText() {
        return raw.toString();
    }

    public boolean isError() {
        return LogLevels.isError(level);
    }

    public boolean isWarning() {
        return LogLevels.WARN.equals(level);
    }

    public boolean hasCorrelation() {
        return correlationId != null || traceId != null;
    }
}
