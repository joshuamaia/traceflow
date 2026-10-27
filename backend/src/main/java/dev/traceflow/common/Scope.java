package dev.traceflow.common;

/**
 * Dataset selector used by read APIs: demo scenario data, imported data or both.
 */
public enum Scope {
    ALL, DEMO, IMPORT;

    public static Scope parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        return switch (value.trim().toUpperCase()) {
            case "DEMO" -> DEMO;
            case "IMPORT", "IMPORTED" -> IMPORT;
            default -> ALL;
        };
    }

    /** Returns the matching source kind name or {@code null} for {@link #ALL}. */
    public String sourceKind() {
        return this == ALL ? null : name();
    }
}
