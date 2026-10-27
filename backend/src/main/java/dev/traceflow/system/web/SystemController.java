package dev.traceflow.system.web;

import java.util.List;

import dev.traceflow.config.TraceflowProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "Server configuration relevant to clients")
public class SystemController {

    public record SystemInfoDto(String version, boolean publicMode, boolean importsEnabled, long maxFileSizeBytes,
                                int maxFiles, List<String> allowedExtensions, long maxPasteSizeBytes,
                                boolean retentionEnabled, int retentionDays, String defaultZone, List<String> parsers) {
    }

    private final TraceflowProperties properties;

    public SystemController(TraceflowProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/info")
    @Operation(summary = "Limits, retention policy and feature flags")
    public SystemInfoDto info() {
        var imports = properties.imports();
        return new SystemInfoDto("1.0.0", properties.publicMode(), !properties.publicMode(),
                imports.maxFileSize().toBytes(), imports.maxFiles(), imports.allowedExtensions(),
                imports.maxPasteSize().toBytes(), properties.retention().enabled(), properties.retention().days(),
                properties.parsing().defaultZone().getId(), List.of("JSON", "SPRING_BOOT", "TEXT"));
    }
}
