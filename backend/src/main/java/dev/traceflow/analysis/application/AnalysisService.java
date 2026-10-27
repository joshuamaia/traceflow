package dev.traceflow.analysis.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import dev.traceflow.analysis.application.AnalysisDtos.AnalysisDto;
import dev.traceflow.analysis.application.AnalysisDtos.JobDto;
import dev.traceflow.analysis.application.AnalysisDtos.LineDto;
import dev.traceflow.analysis.application.AnalysisDtos.LinesDto;
import dev.traceflow.analysis.application.AnalysisDtos.ParseErrorDto;
import dev.traceflow.analysis.domain.Analysis;
import dev.traceflow.analysis.domain.AnalysisFile;
import dev.traceflow.analysis.domain.AnalysisFileRepository;
import dev.traceflow.analysis.domain.AnalysisRepository;
import dev.traceflow.analysis.domain.AnalysisStatus;
import dev.traceflow.analysis.domain.SourceKind;
import dev.traceflow.analysis.infrastructure.FileStorage;
import dev.traceflow.analysis.infrastructure.IngestionStore;
import dev.traceflow.common.web.BadRequestException;
import dev.traceflow.common.web.ForbiddenOperationException;
import dev.traceflow.common.web.NotFoundException;
import dev.traceflow.common.web.PageResponse;
import dev.traceflow.common.web.Paging;
import dev.traceflow.config.TraceflowProperties;
import dev.traceflow.events.domain.ParseErrorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final AnalysisRepository analyses;
    private final AnalysisFileRepository files;
    private final ParseErrorRepository parseErrors;
    private final FileStorage storage;
    private final IngestionStore store;
    private final AnalysisJobs jobs;
    private final TraceflowProperties properties;

    public AnalysisService(AnalysisRepository analyses, AnalysisFileRepository files, ParseErrorRepository parseErrors,
                           FileStorage storage, IngestionStore store, AnalysisJobs jobs,
                           TraceflowProperties properties) {
        this.analyses = analyses;
        this.files = files;
        this.parseErrors = parseErrors;
        this.storage = storage;
        this.store = store;
        this.jobs = jobs;
        this.properties = properties;
    }

    /**
     * Validates and stores the uploaded files (masked) as one analysis, then schedules asynchronous processing.
     */
    public AnalysisDto importFiles(List<MultipartFile> uploads, String requestedName) {
        if (properties.publicMode()) {
            throw new ForbiddenOperationException(
                    "Persistent imports are disabled in public mode. Use the Log Analyzer paste mode or the demo scenarios.");
        }
        var limits = properties.imports();
        List<MultipartFile> files = uploads == null ? List.of()
                : uploads.stream().filter(java.util.Objects::nonNull).toList();
        if (files.isEmpty()) {
            throw new BadRequestException("No files were uploaded");
        }
        if (files.size() > limits.maxFiles()) {
            throw new BadRequestException("Too many files: at most " + limits.maxFiles() + " files per import");
        }
        for (MultipartFile file : files) {
            validateUpload(file);
        }
        String name = requestedName != null && !requestedName.isBlank()
                ? sanitizeFileName(requestedName)
                : files.size() == 1 ? sanitizeFileName(files.getFirst().getOriginalFilename())
                : files.size() + " files (" + sanitizeFileName(files.getFirst().getOriginalFilename()) + ", ...)";
        Analysis analysis = Analysis.create(SourceKind.IMPORT, cut(name, 255));
        try {
            for (MultipartFile upload : files) {
                analysis.addFile(storeUpload(analysis.getId(), upload));
            }
        } catch (IOException e) {
            storage.deleteAnalysis(analysis.getId());
            log.error("Failed to store upload", e);
            throw new BadRequestException("The uploaded file could not be stored");
        } catch (RuntimeException e) {
            storage.deleteAnalysis(analysis.getId());
            throw e;
        }
        analyses.save(analysis);
        jobs.submit(analysis.getId());
        return AnalysisDto.of(analysis);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnalysisDto> list(String kind, int page, int size) {
        var pageable = Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = kind == null || kind.isBlank() || kind.equalsIgnoreCase("ALL")
                ? analyses.findAll(pageable)
                : analyses.findByKind(SourceKind.valueOf(kind.toUpperCase(Locale.ROOT).replace("IMPORTED", "IMPORT")),
                pageable);
        return PageResponse.of(result, AnalysisDto::of);
    }

    @Transactional(readOnly = true)
    public AnalysisDto get(UUID id) {
        return AnalysisDto.of(find(id));
    }

    @Transactional(readOnly = true)
    public JobDto job(UUID id) {
        return JobDto.of(find(id));
    }

    public JobDto reprocess(UUID id) {
        Analysis analysis = find(id);
        if (analysis.getStatus() == AnalysisStatus.PROCESSING || analysis.getStatus() == AnalysisStatus.PENDING) {
            throw new BadRequestException("The analysis is already being processed");
        }
        for (AnalysisFile file : files.findByAnalysisIdOrderByCreatedAtAsc(id)) {
            if (!storage.exists(analysis.getId(), file.getStoredName())) {
                throw new BadRequestException("Stored file '" + file.getOriginalName() + "' is no longer available");
            }
        }
        analysis.resetCounters();
        analysis.setStatus(AnalysisStatus.PENDING);
        analyses.save(analysis);
        jobs.submit(id);
        return JobDto.of(analysis);
    }

    public void delete(UUID id) {
        Analysis analysis = find(id);
        if (analysis.getStatus() == AnalysisStatus.PROCESSING) {
            throw new BadRequestException("The analysis is being processed; wait until it finishes before deleting it");
        }
        store.deleteAnalysis(id);
        storage.deleteAnalysis(id);
        log.info("Deleted analysis {} and its stored files", id);
    }

    @Transactional(readOnly = true)
    public LinesDto lines(UUID analysisId, UUID fileId, int from, int limit) {
        Analysis analysis = find(analysisId);
        AnalysisFile file = analysis.getFiles().stream().filter(f -> f.getId().equals(fileId)).findFirst()
                .orElseThrow(() -> new NotFoundException("File not found in this analysis"));
        int safeLimit = Math.min(Math.max(limit, 1), 2000);
        try {
            var page = storage.readLines(analysisId, file.getStoredName(), from, safeLimit);
            List<LineDto> lines = new ArrayList<>(page.lines().size());
            for (int i = 0; i < page.lines().size(); i++) {
                lines.add(new LineDto(page.fromLine() + i, page.lines().get(i)));
            }
            return new LinesDto(fileId, page.fromLine(), lines, page.hasMore(), file.getLineCount());
        } catch (IOException e) {
            throw new NotFoundException("The stored file is not available");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ParseErrorDto> parseErrors(UUID analysisId, UUID fileId, int page, int size) {
        find(analysisId);
        var pageable = Paging.of(page, size, Sort.by("lineNumber"));
        var result = fileId == null
                ? parseErrors.findByAnalysisId(analysisId, pageable)
                : parseErrors.findByAnalysisIdAndFileId(analysisId, fileId, pageable);
        return PageResponse.of(result, ParseErrorDto::of);
    }

    private Analysis find(UUID id) {
        return analyses.findById(id).orElseThrow(() -> new NotFoundException("Analysis " + id + " not found"));
    }

    private void validateUpload(MultipartFile file) {
        String name = sanitizeFileName(file.getOriginalFilename());
        String extension = extension(name);
        if (!properties.imports().allowedExtensions().contains(extension)) {
            throw new BadRequestException("File '" + name + "' has an unsupported extension. Allowed: "
                    + String.join(", ", properties.imports().allowedExtensions()));
        }
        if (file.getSize() == 0) {
            throw new BadRequestException("File '" + name + "' is empty");
        }
        if (file.getSize() > properties.imports().maxFileSize().toBytes()) {
            throw new BadRequestException("File '" + name + "' exceeds the maximum size of "
                    + properties.imports().maxFileSize().toMegabytes() + " MB");
        }
    }

    private AnalysisFile storeUpload(UUID analysisId, MultipartFile upload) throws IOException {
        String originalName = sanitizeFileName(upload.getOriginalFilename());
        String extension = extension(originalName);
        UUID fileId = UUID.randomUUID();
        String storedName = fileId + "." + extension;
        FileStorage.StoredFile stored;
        try (InputStream in = upload.getInputStream()) {
            stored = storage.storeMasked(analysisId, storedName, in, properties.imports().maxFileSize().toBytes());
        } catch (BadRequestException e) {
            throw new BadRequestException("File '" + originalName + "': " + e.getMessage());
        }
        validateContent(originalName, extension, stored);
        return AnalysisFile.create(fileId, originalName, storedName, extension, stored.sizeBytes());
    }

    /** Content checks complementing the extension check. */
    static void validateContent(String name, String extension, FileStorage.StoredFile stored) {
        if (stored.firstNonBlankLine() == null) {
            throw new BadRequestException("File '" + name + "' contains no log lines");
        }
        char first = stored.firstNonWhitespace();
        if (extension.equals("json") && first != '{' && first != '[') {
            throw new BadRequestException("File '" + name + "' is not valid JSON: expected an object per line or an array");
        }
        if (extension.equals("jsonl") && first != '{') {
            throw new BadRequestException("File '" + name + "' is not valid JSON Lines: each line must be a JSON object");
        }
    }

    /** Keeps only the last path segment and safe characters - names are only displayed, never used as paths. */
    static String sanitizeFileName(String name) {
        if (name == null) {
            return "upload.log";
        }
        String n = name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        n = n.replaceAll("\\p{Cntrl}", "").replaceAll("[^\\w .()\\[\\]-]", "_").trim();
        while (n.startsWith(".")) {
            n = n.substring(1);
        }
        if (n.isBlank()) {
            return "upload.log";
        }
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
