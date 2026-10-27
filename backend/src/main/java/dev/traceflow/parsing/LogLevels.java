package dev.traceflow.parsing;

import java.util.Locale;
import java.util.Set;

/** Normalizes log level names coming from different logging frameworks. */
public final class LogLevels {

    public static final String TRACE = "TRACE";
    public static final String DEBUG = "DEBUG";
    public static final String INFO = "INFO";
    public static final String WARN = "WARN";
    public static final String ERROR = "ERROR";
    public static final String FATAL = "FATAL";
    public static final String UNKNOWN = "UNKNOWN";

    public static final Set<String> ALL = Set.of(TRACE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN);

    private LogLevels() {
    }

    /** @return the normalized level or {@code null} when the token is not a known level name. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "TRACE", "TRC", "FINEST", "FINER" -> TRACE;
            case "DEBUG", "DBG", "FINE", "VERBOSE" -> DEBUG;
            case "INFO", "INF", "INFORMATION", "NOTICE", "CONFIG" -> INFO;
            case "WARN", "WARNING", "WRN" -> WARN;
            case "ERROR", "ERR", "SEVERE" -> ERROR;
            case "FATAL", "CRITICAL", "CRIT", "EMERG", "ALERT", "PANIC" -> FATAL;
            default -> null;
        };
    }

    /** Maps numeric levels (bunyan/pino style) to names. */
    public static String fromNumeric(long value) {
        if (value >= 60) {
            return FATAL;
        }
        if (value >= 50) {
            return ERROR;
        }
        if (value >= 40) {
            return WARN;
        }
        if (value >= 30) {
            return INFO;
        }
        if (value >= 20) {
            return DEBUG;
        }
        return TRACE;
    }

    public static boolean isError(String level) {
        return ERROR.equals(level) || FATAL.equals(level);
    }
}
