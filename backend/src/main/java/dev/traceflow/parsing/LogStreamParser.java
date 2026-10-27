package dev.traceflow.parsing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import dev.traceflow.parsing.parser.JsonLogParser;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectMapper;

/**
 * Incremental log parser. Reads input line by line (or token by token for JSON arrays) and emits events to a
 * {@link Sink} as soon as they are complete, so memory usage does not depend on the file size. Problems are reported
 * and parsing continues with the next line.
 */
public final class LogStreamParser {

    public interface Sink {
        void event(ParsedEvent event);

        void problem(ParseProblem problem);
    }

    public record Options(ZoneId defaultZone, String defaultService, UnaryOperator<String> preprocessor) {
        public Options {
            preprocessor = preprocessor == null ? UnaryOperator.identity() : preprocessor;
        }
    }

    /** Outcome counters of one parse run. */
    public static final class Stats {
        private int lines;
        private int events;
        private int problems;
        private final Map<String, Integer> parserUsage = new HashMap<>();

        public int lines() {
            return lines;
        }

        public int events() {
            return events;
        }

        public int problems() {
            return problems;
        }

        /** Dominant format: the parser that produced most events, or MIXED when no parser reaches 80%. */
        public String detectedFormat() {
            if (events == 0) {
                return "UNKNOWN";
            }
            var top = parserUsage.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
            return top.getValue() * 100 / events >= 80 ? top.getKey() : "MIXED";
        }
    }

    private static final int MAX_STACK_LINES = 2000;

    private final List<LogLineParser> parsers;
    private final JsonLogParser jsonParser;
    private final ObjectMapper mapper;
    /** Streaming element reader: more array elements follow each object, so trailing tokens are expected. */
    private final ObjectReader elementReader;

    public LogStreamParser(ObjectMapper mapper) {
        this.mapper = mapper;
        this.elementReader = mapper.reader().without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.parsers = LogParsers.defaults(mapper);
        this.jsonParser = new JsonLogParser(mapper);
    }

    /** Parses line oriented content: plain text, Spring Boot logs, JSON lines or a mix of them. */
    public Stats parseLines(BufferedReader reader, Options options, Sink sink) throws IOException {
        var stats = new Stats();
        CountingSink counting = new CountingSink(sink, stats);
        ParsedEvent current = null;
        int stackLines = 0;
        int lineNo = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNo++;
            stats.lines = lineNo;
            if (lineNo == 1 && !line.isEmpty() && line.charAt(0) == '﻿') {
                line = line.substring(1);
            }
            line = options.preprocessor().apply(line);
            if (line.isBlank()) {
                continue;
            }
            if (current != null && (StackTraces.isContinuation(line) || StackTraces.isHeader(line))) {
                if (stackLines++ < MAX_STACK_LINES) {
                    current.appendStackLine(line, lineNo);
                } else {
                    current.setLineEnd(lineNo);
                }
                continue;
            }
            ParsedEvent parsed = null;
            String failure = null;
            var context = new ParseContext(lineNo, options.defaultZone(), options.defaultService());
            for (LogLineParser parser : parsers) {
                if (!parser.accepts(line)) {
                    continue;
                }
                try {
                    parsed = parser.parse(line, context);
                } catch (LogParseException e) {
                    failure = e.getMessage();
                    break;
                } catch (RuntimeException e) {
                    failure = "Parser " + parser.name() + " failed: " + e.getClass().getSimpleName();
                    break;
                }
                if (parsed != null) {
                    break;
                }
            }
            if (parsed != null) {
                emit(current, counting);
                current = parsed;
                stackLines = 0;
                if (parsed.isTimestampInvalid()) {
                    counting.problem(ParseProblem.warning(lineNo, "Invalid timestamp '" + parsed.getTimestampRaw()
                            + "' - event kept without timestamp", line));
                }
                continue;
            }
            if (failure == null && current != null && Character.isWhitespace(line.charAt(0))) {
                // indented continuation of a multi-line message
                appendMessageContinuation(current, line, lineNo);
                continue;
            }
            counting.problem(ParseProblem.error(lineNo, failure != null ? failure : "Unrecognized log format", line));
        }
        emit(current, counting);
        return stats;
    }

    /** Parses a file containing a JSON array of log objects, streaming one element at a time. */
    public Stats parseJsonArray(Reader reader, Options options, Sink sink) {
        var stats = new Stats();
        CountingSink counting = new CountingSink(sink, stats);
        try (JsonParser parser = mapper.createParser(reader)) {
            JsonToken token = parser.nextToken();
            if (token != JsonToken.START_ARRAY) {
                counting.problem(ParseProblem.error(1, "Expected a JSON array of log objects", null));
                return stats;
            }
            while ((token = parser.nextToken()) != null && token != JsonToken.END_ARRAY) {
                int startLine = parser.currentTokenLocation().getLineNr();
                if (token != JsonToken.START_OBJECT) {
                    parser.skipChildren();
                    counting.problem(ParseProblem.error(startLine, "Array element is not a JSON object", null));
                    continue;
                }
                JsonNode node = elementReader.readTree(parser);
                int endLine = parser.currentTokenLocation().getLineNr();
                stats.lines = Math.max(stats.lines, endLine);
                String raw = options.preprocessor().apply(node.toString());
                var context = new ParseContext(startLine, options.defaultZone(), options.defaultService());
                ParsedEvent event = jsonParser.fromNode(node, raw, context);
                event.setLineEnd(Math.max(startLine, endLine));
                if (event.isTimestampInvalid()) {
                    counting.problem(ParseProblem.warning(startLine, "Invalid timestamp '" + event.getTimestampRaw()
                            + "' - event kept without timestamp", null));
                }
                emit(event, counting);
            }
        } catch (JacksonException e) {
            int line = e.getLocation() == null ? stats.lines : e.getLocation().getLineNr();
            counting.problem(ParseProblem.error(line, "Malformed JSON array, parsing stopped: "
                    + firstLine(e.getOriginalMessage()), null));
        }
        return stats;
    }

    private static void appendMessageContinuation(ParsedEvent event, String line, int lineNo) {
        event.getRaw().append('\n').append(line);
        event.setLineEnd(lineNo);
        String message = event.getMessage() == null ? "" : event.getMessage();
        event.setMessage(message + "\n" + line.strip());
    }

    private void emit(ParsedEvent event, CountingSink sink) {
        if (event != null) {
            sink.event(event);
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "invalid syntax";
        }
        String first = message.lines().findFirst().orElse("invalid syntax");
        return first.length() > 200 ? first.substring(0, 200) : first;
    }

    private record CountingSink(Sink delegate, Stats stats) {
        void event(ParsedEvent event) {
            stats.events++;
            stats.parserUsage.merge(event.getParser(), 1, Integer::sum);
            delegate.event(event);
        }

        void problem(ParseProblem problem) {
            stats.problems++;
            delegate.problem(problem);
        }
    }
}
