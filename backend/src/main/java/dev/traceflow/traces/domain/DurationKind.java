package dev.traceflow.traces.domain;

/**
 * How a duration was obtained. TraceFlow never fabricates durations:
 * <ul>
 *     <li>{@link #REPORTED} - explicitly logged by the application (e.g. {@code durationMs} on the root span).</li>
 *     <li>{@link #OBSERVED} - difference between the first and last timestamp seen in the logs.</li>
 *     <li>{@link #UNKNOWN} - not enough temporal information.</li>
 * </ul>
 */
public enum DurationKind {
    REPORTED, OBSERVED, UNKNOWN
}
