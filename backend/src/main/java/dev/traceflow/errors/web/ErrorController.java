package dev.traceflow.errors.web;

import java.time.Instant;
import java.util.UUID;

import dev.traceflow.common.Scope;
import dev.traceflow.common.web.PageResponse;
import dev.traceflow.errors.application.ErrorDtos.ErrorDetailDto;
import dev.traceflow.errors.application.ErrorDtos.ErrorFacetsDto;
import dev.traceflow.errors.application.ErrorDtos.ErrorGroupDto;
import dev.traceflow.errors.application.ErrorQueryService;
import dev.traceflow.events.application.EventDto;
import dev.traceflow.events.application.EventFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/errors")
@Tag(name = "Errors", description = "Error Explorer: error events, grouping of similar exceptions, stack trace analysis")
public class ErrorController {

    private final ErrorQueryService service;

    public ErrorController(ErrorQueryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List error events")
    public PageResponse<EventDto> list(
            @RequestParam(defaultValue = "ALL") String scope, @RequestParam(required = false) UUID analysisId,
            @RequestParam(required = false) String service, @RequestParam(required = false) String exceptionClass,
            @RequestParam(required = false) String correlationId, @RequestParam(required = false) String fingerprint,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return this.service.list(filter(scope, analysisId, service, exceptionClass, correlationId, fingerprint, q, from, to),
                page, size);
    }

    @GetMapping("/groups")
    @Operation(summary = "Group similar errors (exception class + normalized message + first application frame)")
    public PageResponse<ErrorGroupDto> groups(
            @RequestParam(defaultValue = "ALL") String scope, @RequestParam(required = false) UUID analysisId,
            @RequestParam(required = false) String service, @RequestParam(required = false) String exceptionClass,
            @RequestParam(required = false) String correlationId, @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return this.service.groups(filter(scope, analysisId, service, exceptionClass, correlationId, null, q, from, to),
                page, size);
    }

    @GetMapping("/facets")
    @Operation(summary = "Services and exception classes present in error events (filter options)")
    public ErrorFacetsDto facets(@RequestParam(defaultValue = "ALL") String scope) {
        return service.facets(Scope.parse(scope));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Error detail with stack trace analysis and probable origin")
    public ErrorDetailDto detail(@PathVariable UUID eventId) {
        return service.detail(eventId);
    }

    private static EventFilter filter(String scope, UUID analysisId, String service, String exceptionClass,
                                      String correlationId, String fingerprint, String q, Instant from, Instant to) {
        return new EventFilter(Scope.parse(scope), analysisId, null, null, correlationId, null, service, null, q, from,
                to, true, exceptionClass, fingerprint);
    }
}
