package dev.traceflow.parsing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Helpers to run the stream parser over in-memory text in tests. */
public final class TestParsing {

    public static final ObjectMapper MAPPER = JsonMapper.builder().build();
    public static final ZoneId UTC = ZoneId.of("UTC");

    public record Result(List<ParsedEvent> events, List<ParseProblem> problems, LogStreamParser.Stats stats) {
    }

    private TestParsing() {
    }

    public static ParseContext context(int line) {
        return new ParseContext(line, UTC, null);
    }

    public static Result parse(String content) {
        return parse(content, null);
    }

    public static Result parse(String content, String defaultService) {
        List<ParsedEvent> events = new ArrayList<>();
        List<ParseProblem> problems = new ArrayList<>();
        var sink = new LogStreamParser.Sink() {
            @Override
            public void event(ParsedEvent event) {
                events.add(event);
            }

            @Override
            public void problem(ParseProblem problem) {
                problems.add(problem);
            }
        };
        var options = new LogStreamParser.Options(UTC, defaultService, null);
        var parser = new LogStreamParser(MAPPER);
        try {
            LogStreamParser.Stats stats = content.stripLeading().startsWith("[")
                    ? parser.parseJsonArray(new StringReader(content), options, sink)
                    : parser.parseLines(new BufferedReader(new StringReader(content)), options, sink);
            return new Result(events, problems, stats);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
