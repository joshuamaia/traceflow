package dev.traceflow.analytics.web;

import java.time.Instant;

import dev.traceflow.analytics.application.AnalyticsDtos.AnalyticsDto;
import dev.traceflow.analytics.application.AnalyticsDtos.DashboardDto;
import dev.traceflow.analytics.application.AnalyticsService;
import dev.traceflow.common.Scope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Dashboard & analytics", description = "Metrics derived from processed events and traces")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard cards and charts")
    public DashboardDto dashboard(
            @Parameter(description = "ALL, DEMO or IMPORT") @RequestParam(defaultValue = "ALL") String scope) {
        return service.dashboard(Scope.parse(scope));
    }

    @GetMapping("/analytics")
    @Operation(summary = "Analytics: volume, error rates, failures, recurring exceptions, duration percentiles")
    public AnalyticsDto analytics(
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.analytics(Scope.parse(scope), from, to);
    }
}
