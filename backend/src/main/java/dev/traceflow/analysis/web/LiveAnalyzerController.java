package dev.traceflow.analysis.web;

import dev.traceflow.analysis.application.LiveAnalyzerService;
import dev.traceflow.analysis.application.LiveAnalyzerService.LiveAnalysisDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analyzer")
@Tag(name = "Live analyzer", description = "Parse pasted log content without storing it")
public class LiveAnalyzerController {

    public record ParseRequest(@NotBlank String content, @Size(max = 200) String fileName) {
    }

    private final LiveAnalyzerService service;

    public LiveAnalyzerController(LiveAnalyzerService service) {
        this.service = service;
    }

    @PostMapping("/parse")
    @Operation(summary = "Parse log content (not persisted) and return parsed events and parse problems")
    public LiveAnalysisDto parse(@Valid @RequestBody ParseRequest request) {
        return service.analyze(request.content(), request.fileName());
    }
}
