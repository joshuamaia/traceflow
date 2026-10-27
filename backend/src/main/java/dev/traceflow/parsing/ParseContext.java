package dev.traceflow.parsing;

import java.time.ZoneId;

/**
 * Per-line information handed to parsers.
 *
 * @param lineNumber     1-based line number of the line being parsed
 * @param defaultZone    zone used for timestamps without offset
 * @param defaultService service name derived from the file name, used when the event declares none
 */
public record ParseContext(int lineNumber, ZoneId defaultZone, String defaultService) {
}
