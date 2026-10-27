package dev.traceflow.events.application;

import java.util.Map;

import dev.traceflow.events.domain.LogEvent;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class EventMapper {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final ObjectMapper mapper;

    public EventMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public EventDto toDto(LogEvent e, boolean detailed) {
        return new EventDto(e.getId(), e.getAnalysisId(), e.getFileId(), e.getTraceRef(), e.getSourceKind(),
                e.getSeq(), e.getLineStart(), e.getLineEnd(), e.getTimestamp(), e.getTsRaw(), e.getLevel(),
                e.getService(), e.getThread(), e.getLogger(), e.getCorrelationId(), e.getTraceId(), e.getSpanId(),
                e.getParentSpanId(), e.getSpanKind(), e.getOperation(), e.getPeerService(), e.getPeerType(),
                e.getMessageId(), e.getMessage(), e.getExceptionClass(), e.getExceptionMessage(),
                e.getStackTrace() != null, detailed ? e.getStackTrace() : null, e.getDurationMs(), e.getHttpStatus(),
                e.isError(), e.getFingerprint(), e.getParser(), attributes(e.getAttributes()),
                detailed ? e.getRaw() : null);
    }

    public Map<String, Object> attributes(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, MAP);
        } catch (JacksonException e) {
            return Map.of("_raw", json);
        }
    }
}
