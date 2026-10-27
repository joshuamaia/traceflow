package dev.traceflow.parsing.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.traceflow.parsing.ContextExtractor;
import dev.traceflow.parsing.LogLevels;
import dev.traceflow.parsing.LogLineParser;
import dev.traceflow.parsing.ParseContext;
import dev.traceflow.parsing.ParsedEvent;
import dev.traceflow.parsing.Timestamps;

/**
 * Spring Boot default console/file layout, including the variants of Boot 2 (Sleuth) and Boot 3/4
 * (application name and Micrometer correlation section):
 *
 * <pre>
 * 2026-10-27T10:15:30.123Z  INFO 4242 --- [order-service] [nio-8080-exec-1] [4bf92f...-00f067...] d.t.OrderController : msg
 * 2026-10-27 10:15:30.123  INFO [order-service,4bf92f...,00f067...] 4242 --- [main] d.t.OrderService : msg
 * </pre>
 */
public final class SpringBootLogParser implements LogLineParser {

    public static final String NAME = "SPRING_BOOT";

    private static final Pattern LINE = Pattern.compile(
            "^(?<ts>" + Timestamps.PATTERN + ")\\s+(?<level>[A-Za-z]{4,7})\\s+"
                    + "(?:\\[(?<sleuth>[^\\]]*)\\]\\s+)?"
                    + "(?<pid>\\d+)\\s+---\\s+"
                    + "(?:\\[(?<app>[^\\]]*)\\]\\s+)?"
                    + "\\[\\s*(?<thread>[^\\]]*?)\\s*\\]\\s+"
                    + "(?:\\[(?<corr>[^\\]]*)\\]\\s+)?"
                    + "(?<logger>[\\w$.\\[\\]/-]+)\\s*:\\s(?<msg>.*)$");

    private static final Pattern HEX_ID = Pattern.compile("[0-9a-fA-F]{8,32}");
    private static final Pattern CORRELATION_SECTION = Pattern.compile("[0-9a-fA-F]{8,32}[-,][0-9a-fA-F]{8,32}");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean accepts(String line) {
        return line.length() > 20 && Character.isDigit(line.charAt(0)) && line.contains("---");
    }

    @Override
    public ParsedEvent parse(String line, ParseContext context) {
        Matcher m = LINE.matcher(line);
        if (!m.matches()) {
            return null;
        }
        String level = LogLevels.normalize(m.group("level"));
        if (level == null) {
            return null;
        }
        var event = new ParsedEvent(NAME, context.lineNumber(), line);
        String ts = m.group("ts");
        event.setTimestampRaw(ts);
        Timestamps.parse(ts, context.defaultZone()).ifPresentOrElse(event::setTimestamp,
                () -> event.setTimestampInvalid(true));
        event.setLevel(level);
        String app = blankToNull(m.group("app"));
        String thread = blankToNull(m.group("thread"));
        String corr = blankToNull(m.group("corr"));
        if (corr == null && app != null && thread != null && CORRELATION_SECTION.matcher(thread).matches()) {
            // "[thread] [traceId-spanId]" without application name: shift the bracket groups
            corr = thread;
            thread = app;
            app = null;
        }
        event.setThread(thread);
        event.setLogger(m.group("logger"));
        event.setMessage(m.group("msg"));
        event.getAttributes().put("pid", m.group("pid"));

        if (app != null) {
            event.setService(app);
        }
        String sleuth = blankToNull(m.group("sleuth"));
        if (sleuth != null) {
            // [application,traceId,spanId,exportable]
            String[] parts = sleuth.split(",");
            if (parts.length > 0 && event.getService() == null && !parts[0].isBlank()) {
                event.setService(parts[0].trim());
            }
            if (parts.length > 1 && HEX_ID.matcher(parts[1].trim()).matches()) {
                event.setTraceId(parts[1].trim());
            }
            if (parts.length > 2 && HEX_ID.matcher(parts[2].trim()).matches()) {
                event.setSpanId(parts[2].trim());
            }
        }
        if (corr != null) {
            // Micrometer tracing: [traceId-spanId]
            String[] parts = corr.split("[-,]");
            if (parts.length >= 1 && HEX_ID.matcher(parts[0].trim()).matches()) {
                event.setTraceId(parts[0].trim());
            }
            if (parts.length >= 2 && HEX_ID.matcher(parts[1].trim()).matches()) {
                event.setSpanId(parts[1].trim());
            }
            if (event.getTraceId() == null) {
                ContextExtractor.apply(corr, event);
            }
        }
        if (event.getService() == null && context.defaultService() != null) {
            event.setService(context.defaultService());
            event.setServiceFromFileName(true);
        }
        ContextExtractor.apply(event.getMessage(), event);
        return event;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
