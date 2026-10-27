package dev.traceflow.parsing.parser;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.traceflow.parsing.ContextExtractor;
import dev.traceflow.parsing.LogLevels;
import dev.traceflow.parsing.LogLineParser;
import dev.traceflow.parsing.LogParseException;
import dev.traceflow.parsing.ParseContext;
import dev.traceflow.parsing.ParsedEvent;
import dev.traceflow.parsing.Timestamps;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Structured JSON logs (one object per line, or objects of a JSON array). Understands the field names used by
 * Logstash/Logback encoders, ECS, OpenTelemetry style attributes and common custom conventions.
 */
public final class JsonLogParser implements LogLineParser {

    public static final String NAME = "JSON";

    private static final List<String> TIMESTAMP = List.of("@timestamp", "timestamp", "time", "ts", "datetime",
            "date", "eventTime", "event_time");
    private static final List<String> LEVEL = List.of("level", "log.level", "severity", "loglevel", "lvl",
            "levelname", "level_name", "severity_text");
    private static final List<String> SERVICE = List.of("service", "service.name", "serviceName", "service_name",
            "app", "application", "app_name", "appName", "component");
    private static final List<String> THREAD = List.of("thread", "thread_name", "threadName", "thread.name");
    private static final List<String> LOGGER = List.of("logger", "logger_name", "loggerName", "log.logger",
            "class", "caller");
    private static final List<String> MESSAGE = List.of("message", "msg", "log", "text", "event");
    private static final List<String> CORRELATION = List.of("correlationId", "correlation_id", "correlation.id",
            "x-correlation-id", "X-Correlation-ID", "mdc.correlationId", "mdc.correlation_id",
            "context.correlationId", "requestId", "request_id", "cid");
    private static final List<String> TRACE_ID = List.of("traceId", "trace_id", "trace.id", "traceid",
            "mdc.traceId", "mdc.trace_id", "dd.trace_id", "X-B3-TraceId");
    private static final List<String> SPAN_ID = List.of("spanId", "span_id", "span.id", "mdc.spanId",
            "mdc.span_id", "dd.span_id", "X-B3-SpanId");
    private static final List<String> PARENT_SPAN_ID = List.of("parentSpanId", "parent_span_id", "parentId",
            "parent_id", "span.parent_id", "X-B3-ParentSpanId");
    private static final List<String> SPAN_KIND = List.of("spanKind", "span_kind", "span.kind", "kind");
    private static final List<String> OPERATION = List.of("operation", "operationName", "operation_name",
            "span.name", "spanName", "span_name");
    private static final List<String> PEER_SERVICE = List.of("peerService", "peer.service", "peer_service",
            "targetService", "target_service");
    private static final List<String> PEER_TYPE = List.of("peerType", "peer.type", "peer_type");
    private static final List<String> MESSAGE_ID = List.of("messageId", "message_id", "messaging.message_id",
            "messaging.message.id");
    private static final List<String> DURATION = List.of("durationMs", "duration_ms", "elapsedMs", "elapsed_ms",
            "latencyMs", "latency_ms", "took_ms", "response_time_ms", "duration");
    private static final List<String> HTTP_STATUS = List.of("httpStatus", "http_status", "http.status",
            "http.status_code", "http.response.status_code", "statusCode", "status_code", "status");
    private static final List<String> STACK = List.of("stack_trace", "stackTrace", "stacktrace", "exception",
            "error.stack_trace", "exception.stacktrace", "error.stack", "throwable");
    private static final List<String> EXCEPTION_CLASS = List.of("exception.type", "error.type", "exceptionClass",
            "exception_class", "error.kind");
    private static final List<String> EXCEPTION_MESSAGE = List.of("exception.message", "error.message",
            "exceptionMessage", "exception_message");

    private static final Set<String> SPAN_KINDS = Set.of("SERVER", "CLIENT", "PRODUCER", "CONSUMER", "INTERNAL");

    private final ObjectMapper mapper;

    public JsonLogParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean accepts(String line) {
        return line.stripLeading().startsWith("{");
    }

    @Override
    public ParsedEvent parse(String line, ParseContext context) {
        JsonNode node;
        try {
            node = mapper.readTree(line);
        } catch (JacksonException e) {
            throw new LogParseException("Malformed JSON: " + shortReason(e));
        }
        if (node == null || !node.isObject()) {
            throw new LogParseException("JSON value is not an object");
        }
        return fromNode(node, line, context);
    }

    /** Converts an already parsed JSON object (also used for JSON array files). */
    public ParsedEvent fromNode(JsonNode node, String raw, ParseContext context) {
        var event = new ParsedEvent(NAME, context.lineNumber(), raw);
        Set<String> consumed = new HashSet<>();

        JsonNode ts = first(node, TIMESTAMP, consumed);
        if (ts != null) {
            if (ts.isNumber()) {
                event.setTimestampRaw(ts.asString());
                Timestamps.fromEpoch(ts.asDouble()).ifPresentOrElse(event::setTimestamp,
                        () -> event.setTimestampInvalid(true));
            } else {
                event.setTimestampRaw(truncate(ts.asString(), 64));
                Timestamps.parse(ts.asString(), context.defaultZone()).ifPresentOrElse(event::setTimestamp,
                        () -> event.setTimestampInvalid(true));
            }
        }

        JsonNode level = first(node, LEVEL, consumed);
        if (level != null) {
            String normalized = level.isNumber() ? LogLevels.fromNumeric(level.asLong())
                    : LogLevels.normalize(level.asString());
            event.setLevel(normalized == null ? LogLevels.UNKNOWN : normalized);
        }

        event.setService(text(node, SERVICE, consumed));
        event.setThread(text(node, THREAD, consumed));
        event.setLogger(text(node, LOGGER, consumed));
        event.setMessage(text(node, MESSAGE, consumed));
        event.setCorrelationId(text(node, CORRELATION, consumed));
        event.setTraceId(text(node, TRACE_ID, consumed));
        event.setSpanId(text(node, SPAN_ID, consumed));
        event.setParentSpanId(text(node, PARENT_SPAN_ID, consumed));
        String kind = text(node, SPAN_KIND, consumed);
        if (kind != null && SPAN_KINDS.contains(kind.toUpperCase(Locale.ROOT))) {
            event.setSpanKind(kind.toUpperCase(Locale.ROOT));
        }
        event.setOperation(text(node, OPERATION, consumed));
        event.setPeerService(text(node, PEER_SERVICE, consumed));
        String peerType = text(node, PEER_TYPE, consumed);
        event.setPeerType(peerType == null ? null : peerType.toLowerCase(Locale.ROOT));
        event.setMessageId(text(node, MESSAGE_ID, consumed));

        JsonNode duration = first(node, DURATION, consumed);
        if (duration != null) {
            if (duration.isNumber()) {
                event.setDurationMs(Math.round(duration.asDouble()));
            } else {
                parseDurationText(duration.asString(), event);
            }
        }
        JsonNode status = first(node, HTTP_STATUS, consumed);
        if (status != null) {
            int value = status.isNumber() ? status.asInt() : parseIntOrZero(status.asString());
            if (value >= 100 && value <= 599) {
                event.setHttpStatus(value);
            } else {
                event.getAttributes().put("status", status.asString());
            }
        }

        String exceptionClass = text(node, EXCEPTION_CLASS, consumed);
        String exceptionMessage = text(node, EXCEPTION_MESSAGE, consumed);
        if (exceptionClass != null) {
            event.setExceptionClass(truncate(exceptionClass, 255));
            event.setExceptionMessage(exceptionMessage);
        }
        JsonNode stack = first(node, STACK, consumed);
        if (stack != null) {
            if (stack.isObject()) {
                if (event.getExceptionClass() == null) {
                    event.setExceptionClass(textOf(stack, "class", "type", "name"));
                    event.setExceptionMessage(textOf(stack, "message", "msg"));
                }
                event.setStackTraceText(textOf(stack, "stacktrace", "stack_trace", "stackTrace", "stack"));
            } else {
                event.setStackTraceText(stack.asString());
            }
        }

        if (event.getService() == null && context.defaultService() != null) {
            event.setService(context.defaultService());
            event.setServiceFromFileName(true);
        }
        if (LogLevels.UNKNOWN.equals(event.getLevel()) && event.getExceptionClass() != null) {
            event.setLevel(LogLevels.ERROR);
        }
        // fall back to identifiers written inside the message itself
        ContextExtractor.apply(event.getMessage(), event);
        collectAttributes(node, "", consumed, event.getAttributes());
        return event;
    }

    private void parseDurationText(String text, ParsedEvent event) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        try {
            if (t.endsWith("ms")) {
                event.setDurationMs(Math.round(Double.parseDouble(t.substring(0, t.length() - 2).trim())));
            } else if (t.endsWith("s")) {
                event.setDurationMs(Math.round(Double.parseDouble(t.substring(0, t.length() - 1).trim()) * 1000));
            } else {
                event.setDurationMs(Math.round(Double.parseDouble(t)));
            }
        } catch (NumberFormatException ignored) {
            event.getAttributes().put("duration", text);
        }
    }

    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Resolves a field by dotted path, supporting both nested objects and flat dotted keys. */
    private static JsonNode lookup(JsonNode node, String path) {
        JsonNode direct = node.get(path);
        if (direct != null) {
            return direct;
        }
        int dot = path.indexOf('.');
        if (dot > 0) {
            JsonNode child = node.get(path.substring(0, dot));
            if (child != null && child.isObject()) {
                return lookup(child, path.substring(dot + 1));
            }
        }
        return null;
    }

    private static JsonNode first(JsonNode node, List<String> paths, Set<String> consumed) {
        for (String path : paths) {
            JsonNode value = lookup(node, path);
            if (value != null && !value.isNull() && !value.isMissingNode()) {
                if (value.isString() && value.asString().isBlank()) {
                    continue;
                }
                consumed.add(path);
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, List<String> paths, Set<String> consumed) {
        // prefer scalar values: "service": {"name": "x"} must resolve service.name, not the object
        for (String path : paths) {
            JsonNode value = lookup(node, path);
            if (value != null && value.isValueNode() && !value.isNull() && !value.asString().isBlank()) {
                consumed.add(path);
                return value.asString();
            }
        }
        JsonNode value = first(node, paths, consumed);
        if (value == null) {
            return null;
        }
        return value.isValueNode() ? value.asString() : value.toString();
    }

    private static String textOf(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.isValueNode() ? value.asString() : value.toString();
            }
        }
        return null;
    }

    private static void collectAttributes(JsonNode node, String prefix, Set<String> consumed,
                                          Map<String, Object> target) {
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (consumed.contains(path)) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (value.isObject() && consumed.stream().anyMatch(c -> c.startsWith(path + "."))) {
                collectAttributes(value, path, consumed, target);
            } else if (value.isValueNode()) {
                target.put(path, value.isNumber() || value.isBoolean() ? value.asString() : value.asString());
            } else {
                target.put(path, value.toString());
            }
        }
    }

    private static String shortReason(JacksonException e) {
        String message = e.getOriginalMessage();
        if (message == null) {
            return "invalid syntax";
        }
        return truncate(message.lines().findFirst().orElse("invalid syntax"), 200);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
