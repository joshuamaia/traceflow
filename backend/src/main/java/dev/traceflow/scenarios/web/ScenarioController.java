package dev.traceflow.scenarios.web;

import java.util.List;
import java.util.UUID;

import dev.traceflow.common.web.PageResponse;
import dev.traceflow.scenarios.application.ScenarioDtos.ScenarioDto;
import dev.traceflow.scenarios.application.ScenarioDtos.ScenarioRunDto;
import dev.traceflow.scenarios.application.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenarios")
@Tag(name = "Demo scenarios", description = "Execute deterministic demo scenarios and inspect their runs")
public class ScenarioController {

    private final ScenarioService service;

    public ScenarioController(ScenarioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List the available scenarios with their latest run")
    public List<ScenarioDto> catalog() {
        return service.catalog();
    }

    @PostMapping("/{key}/execute")
    @Operation(summary = "Execute a scenario; returns the run with its correlationId and trace reference")
    public ResponseEntity<ScenarioRunDto> execute(@PathVariable String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.execute(key));
    }

    @GetMapping("/runs")
    @Operation(summary = "Scenario execution history")
    public PageResponse<ScenarioRunDto> runs(@RequestParam(required = false) String key,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.history(key, page, size);
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "Get a scenario run")
    public ScenarioRunDto run(@PathVariable UUID id) {
        return service.run(id);
    }
}
