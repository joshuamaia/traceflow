package dev.traceflow.analysis.web;

import java.util.List;
import java.util.UUID;

import dev.traceflow.analysis.application.AnalysisDtos.AnalysisDto;
import dev.traceflow.analysis.application.AnalysisDtos.JobDto;
import dev.traceflow.analysis.application.AnalysisDtos.LinesDto;
import dev.traceflow.analysis.application.AnalysisDtos.ParseErrorDto;
import dev.traceflow.analysis.application.AnalysisService;
import dev.traceflow.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Tag(name = "Imports & analyses", description = "Upload log files, follow processing jobs, reprocess and delete analyses")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload one or more log files (.log, .txt, .json, .jsonl)",
            description = "Files are validated, masked and stored, then processed asynchronously. "
                    + "Poll GET /api/analyses/{id}/job for progress.")
    public ResponseEntity<AnalysisDto> upload(@RequestPart("files") List<MultipartFile> files,
                                              @RequestParam(required = false) String name) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.importFiles(files, name));
    }

    @GetMapping("/analyses")
    @Operation(summary = "List analyses (import history and demo runs)")
    public PageResponse<AnalysisDto> list(
            @Parameter(description = "ALL, IMPORT or DEMO") @RequestParam(defaultValue = "ALL") String kind,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(kind, page, size);
    }

    @GetMapping("/analyses/{id}")
    @Operation(summary = "Get an analysis with its files")
    public AnalysisDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/analyses/{id}/job")
    @Operation(summary = "Processing job status and progress")
    public JobDto job(@PathVariable UUID id) {
        return service.job(id);
    }

    @PostMapping("/analyses/{id}/reprocess")
    @Operation(summary = "Reprocess the stored files of an analysis")
    public ResponseEntity<JobDto> reprocess(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.reprocess(id));
    }

    @DeleteMapping("/analyses/{id}")
    @Operation(summary = "Delete an analysis, its events, traces and stored files")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/analyses/{id}/files/{fileId}/lines")
    @Operation(summary = "Read a window of raw (masked) lines of a stored file")
    public LinesDto lines(@PathVariable UUID id, @PathVariable UUID fileId,
                          @RequestParam(defaultValue = "1") int from, @RequestParam(defaultValue = "500") int limit) {
        return service.lines(id, fileId, from, limit);
    }

    @GetMapping("/analyses/{id}/parse-errors")
    @Operation(summary = "Lines that could not be (fully) parsed")
    public PageResponse<ParseErrorDto> parseErrors(@PathVariable UUID id,
                                                   @RequestParam(required = false) UUID fileId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "100") int size) {
        return service.parseErrors(id, fileId, page, size);
    }
}
