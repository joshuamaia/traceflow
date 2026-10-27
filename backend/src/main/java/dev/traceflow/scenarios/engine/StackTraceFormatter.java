package dev.traceflow.scenarios.engine;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Renders a Java stack trace in the standard JVM format, keeping the frames of the simulated services and collapsing
 * the frames of the TraceFlow runtime that executes them (they are not part of the simulated application).
 */
public final class StackTraceFormatter {

    private static final String SIMULATED_PACKAGE = "dev.traceflow.scenarios.simulated";
    private static final int MAX_FRAMES = 25;

    private StackTraceFormatter() {
    }

    public static String format(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        Throwable current = error;
        boolean first = true;
        while (current != null && !seen.containsKey(current)) {
            seen.put(current, true);
            if (!first) {
                sb.append("\nCaused by: ");
            }
            sb.append(current.getClass().getName());
            if (current.getMessage() != null) {
                sb.append(": ").append(current.getMessage());
            }
            StackTraceElement[] frames = current.getStackTrace();
            int kept = 0;
            int lastSimulated = -1;
            for (int i = 0; i < frames.length; i++) {
                if (frames[i].getClassName().startsWith(SIMULATED_PACKAGE)) {
                    lastSimulated = i;
                }
            }
            int limit = lastSimulated >= 0 ? lastSimulated + 1 : Math.min(frames.length, 5);
            for (int i = 0; i < limit && kept < MAX_FRAMES; i++) {
                StackTraceElement f = frames[i];
                sb.append("\n\tat ").append(f.getClassName()).append('.').append(f.getMethodName()).append('(')
                        .append(f.getFileName() == null ? "Unknown Source" : f.getFileName());
                if (f.getLineNumber() > 0) {
                    sb.append(':').append(f.getLineNumber());
                }
                sb.append(')');
                kept++;
            }
            int omitted = frames.length - kept;
            if (omitted > 0) {
                sb.append("\n\t... ").append(omitted).append(" more");
            }
            current = current.getCause();
            first = false;
        }
        return sb.toString();
    }
}
