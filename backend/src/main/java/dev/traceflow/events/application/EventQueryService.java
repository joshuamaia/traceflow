package dev.traceflow.events.application;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import dev.traceflow.common.web.NotFoundException;
import dev.traceflow.common.web.PageResponse;
import dev.traceflow.common.web.Paging;
import dev.traceflow.events.domain.LogEvent;
import dev.traceflow.events.domain.LogEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventQueryService {

    public static final int MAX_EXPORT_ROWS = 100_000;

    private final LogEventRepository events;
    private final EventMapper mapper;

    public EventQueryService(LogEventRepository events, EventMapper mapper) {
        this.events = events;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<EventDto> search(EventFilter filter, int page, int size, String sort, boolean includeRaw) {
        var pageable = Paging.of(page, size, sortOf(sort));
        return PageResponse.of(events.findAll(filter.toSpecification(), pageable), e -> mapper.toDto(e, includeRaw));
    }

    @Transactional(readOnly = true)
    public EventDto get(UUID id) {
        return events.findById(id).map(e -> mapper.toDto(e, true))
                .orElseThrow(() -> new NotFoundException("Event " + id + " not found"));
    }

    /** Streams matching events as CSV, page by page, without loading the result set in memory. */
    public void exportCsv(EventFilter filter, OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write("timestamp,level,service,correlationId,traceId,spanId,operation,durationMs,httpStatus,"
                + "exceptionClass,message,logger,thread,line\n");
        int page = 0;
        int written = 0;
        while (written < MAX_EXPORT_ROWS) {
            var slice = events.findAll(filter.toSpecification(), PageRequest.of(page++, 1000, sortOf("timestamp")));
            for (LogEvent e : slice.getContent()) {
                writer.write(String.join(",", csv(e.getTimestamp() == null ? e.getTsRaw() : e.getTimestamp().toString()),
                        csv(e.getLevel()), csv(e.getService()), csv(e.getCorrelationId()), csv(e.getTraceId()),
                        csv(e.getSpanId()), csv(e.getOperation()),
                        csv(e.getDurationMs() == null ? null : e.getDurationMs().toString()),
                        csv(e.getHttpStatus() == null ? null : e.getHttpStatus().toString()),
                        csv(e.getExceptionClass()), csv(e.getMessage()), csv(e.getLogger()), csv(e.getThread()),
                        csv(String.valueOf(e.getLineStart()))));
                writer.write('\n');
                written++;
            }
            if (!slice.hasNext()) {
                break;
            }
        }
        writer.flush();
    }

    static Sort sortOf(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.asc("timestamp").nullsLast(), Sort.Order.asc("seq"));
        }
        boolean desc = sort.startsWith("-");
        String field = desc ? sort.substring(1) : sort;
        String property = switch (field) {
            case "timestamp", "level", "service", "durationMs", "httpStatus", "seq", "lineStart" -> field;
            default -> "timestamp";
        };
        var order = desc ? Sort.Order.desc(property) : Sort.Order.asc(property);
        return Sort.by(order.nullsLast(), Sort.Order.asc("seq"));
    }

    /** CSV escaping with protection against spreadsheet formula injection. */
    static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\r", " ").replace("\n", " ");
        if (!v.isEmpty() && "=+-@\t".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains(";")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
