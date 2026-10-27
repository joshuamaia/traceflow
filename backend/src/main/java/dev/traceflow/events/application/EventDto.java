package dev.traceflow.events.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * API representation of a log event. {@code stackTrace} and {@code raw} are only filled on detail requests (or when
 * explicitly requested) to keep list payloads small.
 */
public record EventDto(UUID id, UUID analysisId, UUID fileId, UUID traceRef, String sourceKind, long seq,
                       int lineStart, int lineEnd, Instant timestamp, String timestampRaw, String level,
                       String service, String thread, String logger, String correlationId, String traceId,
                       String spanId, String parentSpanId, String spanKind, String operation, String peerService,
                       String peerType, String messageId, String message, String exceptionClass,
                       String exceptionMessage, boolean hasStackTrace, String stackTrace, Long durationMs,
                       Integer httpStatus, boolean error, String fingerprint, String parser,
                       Map<String, Object> attributes, String raw) {
}
