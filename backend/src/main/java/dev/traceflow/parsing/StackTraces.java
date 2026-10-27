package dev.traceflow.parsing;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognition helpers for Java stack traces spread over several lines. */
public final class StackTraces {

    private static final Pattern FRAME = Pattern.compile("^\\s+at\\s+\\S.*");
    private static final Pattern MORE = Pattern.compile("^\\s*\\.\\.\\.\\s*\\d+\\s+(more|common frames omitted).*");
    private static final Pattern CAUSED_BY = Pattern.compile("^\\s*(Caused by|Suppressed):\\s+.*");
    private static final Pattern HEADER = Pattern.compile(
            "^\\s*(?:(?:Caused by|Suppressed):\\s+)?((?:[a-zA-Z_$][\\w$]*\\.)+[A-Za-z_$][\\w$]*"
                    + "(?:Exception|Error|Throwable|Fault|Failure))(?::\\s?(.*))?$");

    public record Header(String className, String message) {
    }

    private StackTraces() {
    }

    /** True for lines that can only be part of a stack trace (frames, "Caused by", "... n more"). */
    public static boolean isContinuation(String line) {
        return FRAME.matcher(line).matches() || MORE.matcher(line).matches() || CAUSED_BY.matcher(line).matches();
    }

    /** True for the first line of a stack trace, e.g. {@code java.sql.SQLException: duplicate key}. */
    public static boolean isHeader(String line) {
        return HEADER.matcher(line).matches();
    }

    public static Optional<Header> parseHeader(String line) {
        if (line == null) {
            return Optional.empty();
        }
        Matcher m = HEADER.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        String message = m.group(2);
        return Optional.of(new Header(m.group(1), message == null || message.isBlank() ? null : message.trim()));
    }
}
