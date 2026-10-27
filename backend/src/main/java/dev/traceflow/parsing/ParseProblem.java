package dev.traceflow.parsing;

/**
 * A recoverable parsing issue. {@code ERROR}: the line was skipped. {@code WARNING}: the event was kept but some
 * information (e.g. an invalid timestamp) could not be interpreted.
 */
public record ParseProblem(int lineNumber, Severity severity, String reason, String raw) {

    public enum Severity {
        ERROR, WARNING
    }

    public static ParseProblem error(int line, String reason, String raw) {
        return new ParseProblem(line, Severity.ERROR, reason, raw);
    }

    public static ParseProblem warning(int line, String reason, String raw) {
        return new ParseProblem(line, Severity.WARNING, reason, raw);
    }
}
