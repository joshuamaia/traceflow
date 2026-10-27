package dev.traceflow.parsing;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import dev.traceflow.parsing.parser.ClassicTextLogParser;
import dev.traceflow.parsing.parser.JsonLogParser;
import dev.traceflow.parsing.parser.SpringBootLogParser;
import tools.jackson.databind.ObjectMapper;

/** Registry of the built-in parsers, ordered by priority. */
public final class LogParsers {

    private LogParsers() {
    }

    public static List<LogLineParser> defaults(ObjectMapper mapper) {
        return Stream.<LogLineParser>of(new JsonLogParser(mapper), new SpringBootLogParser(), new ClassicTextLogParser())
                .sorted(Comparator.comparingInt(LogLineParser::priority))
                .toList();
    }
}
