package dev.traceflow.events.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.traceflow.common.Scope;
import dev.traceflow.common.web.PageResponse;
import dev.traceflow.events.application.EventDto;
import dev.traceflow.events.application.EventFilter;
import dev.traceflow.events.application.EventQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events", description = "Search parsed log events and export them")
public class EventController {

    private final EventQueryService service;

    public EventController(EventQueryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search events with filters and pagination")
    public PageResponse<EventDto> search(
            @Parameter(description = "ALL, DEMO or IMPORT") @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) UUID analysisId, @RequestParam(required = false) UUID fileId,
            @RequestParam(required = false) UUID traceRef, @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String traceId, @RequestParam(required = false) String service,
            @RequestParam(required = false) List<String> level, @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "false") boolean errorsOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "timestamp, -timestamp, level, service, durationMs, lineStart")
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "false") boolean includeRaw) {
        var filter = new EventFilter(Scope.parse(scope), analysisId, fileId, traceRef, correlationId, traceId, service,
                level, q, from, to, errorsOnly, null, null);
        return this.service.search(filter, page, size, sort, includeRaw);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an event including raw content and stack trace")
    public EventDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @Operation(summary = "Export filtered events as CSV (max 100,000 rows)")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) UUID analysisId, @RequestParam(required = false) UUID fileId,
            @RequestParam(required = false) UUID traceRef, @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String traceId, @RequestParam(required = false) String service,
            @RequestParam(required = false) List<String> level, @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "false") boolean errorsOnly,
            @RequestParam(required = false) String exceptionClass,
            @RequestParam(required = false) String fingerprint) {
        var filter = new EventFilter(Scope.parse(scope), analysisId, fileId, traceRef, correlationId, traceId, service,
                level, q, from, to, errorsOnly, exceptionClass, fingerprint);
        StreamingResponseBody body = out -> this.service.exportCsv(filter, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"traceflow-events.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }
}
