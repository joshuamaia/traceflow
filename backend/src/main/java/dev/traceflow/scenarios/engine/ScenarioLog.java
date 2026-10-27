package dev.traceflow.scenarios.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

/**
 * Writes structured JSON log lines (one object per line), the format produced by Logstash style encoders.
 * Thread safe: simulated asynchronous consumers log from their own threads.
 */
public final class ScenarioLog {

    private final Writer writer;
    private final ObjectMapper mapper;
    private int lines;

    public ScenarioLog(Writer writer, ObjectMapper mapper) {
        this.writer = writer;
        this.mapper = mapper;
    }

    public synchronized void write(Map<String, Object> fields) {
        Map<String, Object> clean = new LinkedHashMap<>();
        fields.forEach((k, v) -> {
            if (v != null) {
                clean.put(k, v);
            }
        });
        try {
            writer.write(mapper.writeValueAsString(clean));
            writer.write('\n');
            lines++;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized int lines() {
        return lines;
    }
}
