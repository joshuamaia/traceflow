package dev.traceflow.events.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single normalized log event. Events are written in bulk through JDBC by the ingestion pipeline and read through
 * JPA by the query services.
 */
@Entity
@Table(name = "log_events")
@Getter
@Setter
@NoArgsConstructor
public class LogEvent {

    @Id
    private UUID id;
    private UUID analysisId;
    private UUID fileId;
    private String sourceKind;
    private UUID traceRef;
    private String correlationKey;
    private long seq;
    private int lineStart;
    private int lineEnd;
    @Column(name = "event_ts")
    private Instant timestamp;
    private String tsRaw;
    private String level;
    private String service;
    private String thread;
    private String logger;
    private String correlationId;
    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String spanKind;
    private String operation;
    private String peerService;
    private String peerType;
    private String messageId;
    private String message;
    private String exceptionClass;
    private String exceptionMessage;
    private String stackTrace;
    private Long durationMs;
    private Integer httpStatus;
    @Column(name = "is_error")
    private boolean error;
    private String fingerprint;
    private String attributes;
    private String raw;
    private String parser;
}
