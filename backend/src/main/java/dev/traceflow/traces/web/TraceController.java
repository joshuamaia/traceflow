package dev.traceflow.traces.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.traceflow.common.Scope;
import dev.traceflow.common.web.PageResponse;
import dev.traceflow.traces.application.TraceDtos.SearchResultDto;
import dev.traceflow.traces.application.TraceDtos.TraceDetailDto;
import dev.traceflow.traces.application.TraceDtos.TraceSummaryDto;
import dev.traceflow.traces.application.TraceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Traces", description = "Correlated requests: list, detail with graph/waterfall/timeline, lookup by ids")
public class TraceController {

    private final TraceQueryService service;

    public TraceController(TraceQueryService service) {
        this.service = service;
    }

    @GetMapping("/traces")
    @Operation(summary = "List traces with filters and pagination")
    public PageResponse<TraceSummaryDto> list(
            @Parameter(description = "ALL, DEMO or IMPORT") @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) UUID analysisId,
            @Parameter(description = "correlationId/traceId prefix, operation or service text")
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String service,
            @Parameter(description = "OK, WARNING or ERROR") @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "startedAt, -startedAt, durationMs, -durationMs, errorCount, eventCount")
            @RequestParam(required = false) String sort) {
        var filter = new TraceQueryService.TraceFilter(Scope.parse(scope), analysisId, q, service, status, from, to);
        return this.service.list(filter, page, size, sort);
    }

    @GetMapping("/traces/{id}")
    @Operation(summary = "Trace detail: service graph, spans (waterfall), timeline and relationship evidence")
    public TraceDetailDto detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @GetMapping("/traces/by-correlation/{correlationId}")
    @Operation(summary = "Find traces by correlationId")
    public List<TraceSummaryDto> byCorrelation(@PathVariable String correlationId) {
        return service.byCorrelationId(correlationId);
    }

    @GetMapping("/traces/by-trace-id/{traceId}")
    @Operation(summary = "Find traces by traceId")
    public List<TraceSummaryDto> byTraceId(@PathVariable String traceId) {
        return service.byTraceId(traceId);
    }

    @GetMapping("/search")
    @Operation(summary = "Global search by correlationId or traceId (prefix)")
    public SearchResultDto search(@RequestParam String q) {
        return service.search(q);
    }
}
