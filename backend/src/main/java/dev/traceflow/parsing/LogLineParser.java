package dev.traceflow.parsing;

/**
 * Extension point for log formats. Implementations are tried in {@link #priority()} order; the first one that
 * returns an event wins. Register a new format by adding an implementation to {@link LogParsers#defaults()}.
 */
public interface LogLineParser {

    /** Identifier stored with every event produced by this parser. */
    String name();

    /** Lower values are tried first. */
    int priority();

    /** Cheap pre-check, avoids running expensive matching on unrelated lines. */
    boolean accepts(String line);

    /**
     * @return the parsed event, or {@code null} when the line does not match this format
     * @throws LogParseException when the line clearly belongs to this format but is malformed
     */
    ParsedEvent parse(String line, ParseContext context);
}
