package dev.traceflow.scenarios.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A unit of work of a simulated service. Every log line carries correlationId, traceId, spanId and parentSpanId, and
 * {@link #end} writes the completion line with the measured {@code durationMs} - like an instrumented application.
 */
public final class Span {

    private final ScenarioContext ctx;
    private final String spanId;
    private final String parentSpanId;
    private final String service;
    private final String serviceType;
    private final String operation;
    private final String kind;
    private final Instant start;
    private String thread;
    private String peerService;
    private String peerType;
    private String messageId;
    private boolean ended;

    Span(ScenarioContext ctx, String spanId, String parentSpanId, String service, String serviceType, String operation,
         String kind) {
        this.ctx = ctx;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.service = service;
        this.serviceType = serviceType;
        this.operation = operation;
        this.kind = kind;
        this.start = ctx.clock().now();
        this.thread = defaultThread(service);
    }

    public Span thread(String thread) {
        this.thread = thread;
        return this;
    }

    public Span peer(String peerService, String peerType) {
        this.peerService = peerService;
        this.peerType = peerType;
        return this;
    }

    public Span messageId(String messageId) {
        this.messageId = messageId;
        return this;
    }

    public void info(String logger, String message) {
        write("INFO", logger, message, null, null, null);
    }

    public void info(String logger, String message, Map<String, Object> extra) {
        write("INFO", logger, message, null, null, extra);
    }

    public void debug(String logger, String message) {
        write("DEBUG", logger, message, null, null, null);
    }

    public void warn(String logger, String message, Map<String, Object> extra) {
        write("WARN", logger, message, null, null, extra);
    }

    public void error(String logger, String message, Throwable error, Map<String, Object> extra) {
        write("ERROR", logger, message, error, null, extra);
    }

    /** Writes the completion line, including the measured duration and optional HTTP status. */
    public long end(String level, String logger, String message, Integer httpStatus, Throwable error,
                    Map<String, Object> extra) {
        if (ended) {
            throw new IllegalStateException("Span " + spanId + " already ended");
        }
        ended = true;
        long duration = Duration.between(start, ctx.clock().now()).toMillis();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("durationMs", duration);
        fields.put("httpStatus", httpStatus);
        if (extra != null) {
            fields.putAll(extra);
        }
        write(level, logger, message, error, fields, null);
        return duration;
    }

    private void write(String level, String logger, String message, Throwable error, Map<String, Object> base,
                       Map<String, Object> extra) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("@timestamp", ctx.clock().now().toString());
        line.put("level", level);
        line.put("service", service);
        line.put("serviceType", serviceType);
        line.put("thread", thread);
        line.put("logger", logger);
        line.put("correlationId", ctx.correlationId());
        line.put("traceId", ctx.traceId());
        line.put("spanId", spanId);
        line.put("parentSpanId", parentSpanId);
        line.put("spanKind", kind);
        line.put("operation", operation);
        line.put("peerService", peerService);
        line.put("peerType", peerType);
        line.put("messageId", messageId);
        line.put("message", message);
        if (base != null) {
            line.putAll(base);
        }
        if (extra != null) {
            line.putAll(extra);
        }
        if (error != null) {
            line.put("stack_trace", StackTraceFormatter.format(error));
        }
        ctx.log().write(line);
    }

    public String spanId() {
        return spanId;
    }

    public String service() {
        return service;
    }

    public Instant start() {
        return start;
    }

    private static String defaultThread(String service) {
        return switch (service) {
            case "api-gateway" -> "reactor-http-nio-3";
            case "message-broker" -> "broker-io-1";
            default -> "http-nio-8080-exec-" + (Math.abs(service.hashCode()) % 9 + 1);
        };
    }
}
