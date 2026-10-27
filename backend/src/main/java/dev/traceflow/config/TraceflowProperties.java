package dev.traceflow.config;

import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Application specific configuration, bound from the {@code traceflow.*} namespace.
 */
@ConfigurationProperties(prefix = "traceflow")
public record TraceflowProperties(
        String storageDir,
        boolean publicMode,
        Imports imports,
        Parsing parsing,
        Retention retention,
        Demo demo,
        Cors cors) {

    public TraceflowProperties {
        if (storageDir == null || storageDir.isBlank()) {
            storageDir = "./data/uploads";
        }
        imports = imports == null ? new Imports(null, 0, null, 0, null) : imports;
        parsing = parsing == null ? new Parsing(null, 0) : parsing;
        retention = retention == null ? new Retention(false, 30, null) : retention;
        demo = demo == null ? new Demo(true, 200) : demo;
        cors = cors == null ? new Cors(List.of("http://localhost:4200")) : cors;
    }

    public record Imports(DataSize maxFileSize, int maxFiles, List<String> allowedExtensions, int batchSize,
                          DataSize maxPasteSize) {
        public Imports {
            maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(50) : maxFileSize;
            maxFiles = maxFiles <= 0 ? 10 : maxFiles;
            allowedExtensions = allowedExtensions == null || allowedExtensions.isEmpty()
                    ? List.of("log", "txt", "json", "jsonl") : allowedExtensions;
            batchSize = batchSize <= 0 ? 500 : batchSize;
            maxPasteSize = maxPasteSize == null ? DataSize.ofMegabytes(2) : maxPasteSize;
        }
    }

    public record Parsing(ZoneId defaultZone, int maxEventsPerTraceDetail) {
        public Parsing {
            defaultZone = defaultZone == null ? ZoneId.of("UTC") : defaultZone;
            maxEventsPerTraceDetail = maxEventsPerTraceDetail <= 0 ? 5000 : maxEventsPerTraceDetail;
        }
    }

    public record Retention(boolean enabled, int days, String cron) {
        public Retention {
            days = days <= 0 ? 30 : days;
            cron = cron == null || cron.isBlank() ? "0 0 3 * * *" : cron;
        }
    }

    public record Demo(boolean seedOnStartup, int maxRunsKept) {
        public Demo {
            maxRunsKept = maxRunsKept <= 0 ? 200 : maxRunsKept;
        }
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins;
        }
    }
}
