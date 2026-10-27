-- TraceFlow initial schema.
-- Kept portable between PostgreSQL (default) and H2 in PostgreSQL mode (local profile and tests).

CREATE TABLE analyses (
    id                  UUID PRIMARY KEY,
    kind                VARCHAR(16)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    total_bytes         BIGINT       NOT NULL DEFAULT 0,
    processed_bytes     BIGINT       NOT NULL DEFAULT 0,
    lines_processed     BIGINT       NOT NULL DEFAULT 0,
    events_count        BIGINT       NOT NULL DEFAULT 0,
    traces_count        BIGINT       NOT NULL DEFAULT 0,
    error_events_count  BIGINT       NOT NULL DEFAULT 0,
    parse_errors_count  BIGINT       NOT NULL DEFAULT 0,
    failure_reason      VARCHAR(1000),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at          TIMESTAMP WITH TIME ZONE,
    finished_at         TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_analyses_kind_created ON analyses (kind, created_at);

CREATE TABLE analysis_files (
    id                  UUID PRIMARY KEY,
    analysis_id         UUID         NOT NULL REFERENCES analyses (id) ON DELETE CASCADE,
    original_name       VARCHAR(255) NOT NULL,
    stored_name         VARCHAR(255) NOT NULL,
    extension           VARCHAR(16)  NOT NULL,
    size_bytes          BIGINT       NOT NULL,
    line_count          BIGINT       NOT NULL DEFAULT 0,
    events_count        BIGINT       NOT NULL DEFAULT 0,
    parse_errors_count  BIGINT       NOT NULL DEFAULT 0,
    detected_format     VARCHAR(32),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_analysis_files_analysis ON analysis_files (analysis_id);

CREATE TABLE log_events (
    id                  UUID PRIMARY KEY,
    analysis_id         UUID         NOT NULL REFERENCES analyses (id) ON DELETE CASCADE,
    file_id             UUID         NOT NULL REFERENCES analysis_files (id) ON DELETE CASCADE,
    source_kind         VARCHAR(16)  NOT NULL,
    trace_ref           UUID,
    correlation_key     VARCHAR(128),
    seq                 BIGINT       NOT NULL,
    line_start          INTEGER      NOT NULL,
    line_end            INTEGER      NOT NULL,
    event_ts            TIMESTAMP WITH TIME ZONE,
    ts_raw              VARCHAR(64),
    level               VARCHAR(16)  NOT NULL,
    service             VARCHAR(128),
    thread              VARCHAR(128),
    logger              VARCHAR(255),
    correlation_id      VARCHAR(128),
    trace_id            VARCHAR(64),
    span_id             VARCHAR(64),
    parent_span_id      VARCHAR(64),
    span_kind           VARCHAR(16),
    operation           VARCHAR(255),
    peer_service        VARCHAR(128),
    peer_type           VARCHAR(32),
    message_id          VARCHAR(128),
    message             TEXT,
    exception_class     VARCHAR(255),
    exception_message   TEXT,
    stack_trace         TEXT,
    duration_ms         BIGINT,
    http_status         INTEGER,
    is_error            BOOLEAN      NOT NULL,
    fingerprint         VARCHAR(40),
    attributes          TEXT,
    raw                 TEXT,
    parser              VARCHAR(32)  NOT NULL
);
CREATE INDEX idx_events_file_line ON log_events (file_id, line_start);
CREATE INDEX idx_events_analysis_key ON log_events (analysis_id, correlation_key);
CREATE INDEX idx_events_ts ON log_events (event_ts);
CREATE INDEX idx_events_kind_ts ON log_events (source_kind, event_ts);
CREATE INDEX idx_events_correlation ON log_events (correlation_id);
CREATE INDEX idx_events_trace_id ON log_events (trace_id);
CREATE INDEX idx_events_service ON log_events (service);
CREATE INDEX idx_events_trace_ref ON log_events (trace_ref, event_ts);
CREATE INDEX idx_events_error_ts ON log_events (is_error, event_ts);
CREATE INDEX idx_events_fingerprint ON log_events (fingerprint);

CREATE TABLE traces (
    id                  UUID PRIMARY KEY,
    analysis_id         UUID         NOT NULL REFERENCES analyses (id) ON DELETE CASCADE,
    source_kind         VARCHAR(16)  NOT NULL,
    correlation_key     VARCHAR(128) NOT NULL,
    correlation_id      VARCHAR(128),
    trace_id            VARCHAR(64),
    root_service        VARCHAR(128),
    root_operation      VARCHAR(255),
    started_at          TIMESTAMP WITH TIME ZONE,
    ended_at            TIMESTAMP WITH TIME ZONE,
    duration_ms         BIGINT,
    duration_kind       VARCHAR(16)  NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    http_status         INTEGER,
    event_count         INTEGER      NOT NULL,
    error_count         INTEGER      NOT NULL,
    warn_count          INTEGER      NOT NULL,
    service_count       INTEGER      NOT NULL,
    services            VARCHAR(2000),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_traces_analysis ON traces (analysis_id);
CREATE INDEX idx_traces_kind_started ON traces (source_kind, started_at);
CREATE INDEX idx_traces_correlation ON traces (correlation_id);
CREATE INDEX idx_traces_trace_id ON traces (trace_id);
CREATE INDEX idx_traces_started ON traces (started_at);
CREATE INDEX idx_traces_status ON traces (status);

CREATE TABLE parse_errors (
    id                  UUID PRIMARY KEY,
    analysis_id         UUID         NOT NULL REFERENCES analyses (id) ON DELETE CASCADE,
    file_id             UUID         NOT NULL REFERENCES analysis_files (id) ON DELETE CASCADE,
    line_number         INTEGER      NOT NULL,
    severity            VARCHAR(16)  NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    raw                 TEXT
);
CREATE INDEX idx_parse_errors_file_line ON parse_errors (file_id, line_number);
CREATE INDEX idx_parse_errors_analysis ON parse_errors (analysis_id);

CREATE TABLE scenario_runs (
    id                  UUID PRIMARY KEY,
    scenario_key        VARCHAR(64)  NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    analysis_id         UUID REFERENCES analyses (id) ON DELETE CASCADE,
    trace_ref           UUID,
    correlation_id      VARCHAR(128),
    trace_id            VARCHAR(64),
    http_status         INTEGER,
    outcome             VARCHAR(500),
    details             TEXT,
    failure_reason      VARCHAR(1000),
    started_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at         TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_scenario_runs_key_started ON scenario_runs (scenario_key, started_at);
