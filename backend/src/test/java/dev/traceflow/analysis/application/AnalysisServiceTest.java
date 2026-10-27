package dev.traceflow.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Collections;
import java.util.List;

import dev.traceflow.analysis.domain.AnalysisFileRepository;
import dev.traceflow.analysis.domain.AnalysisRepository;
import dev.traceflow.analysis.infrastructure.FileStorage;
import dev.traceflow.analysis.infrastructure.IngestionStore;
import dev.traceflow.common.web.BadRequestException;
import dev.traceflow.common.web.ForbiddenOperationException;
import dev.traceflow.config.TraceflowProperties;
import dev.traceflow.events.domain.ParseErrorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    AnalysisRepository analyses;
    @Mock
    AnalysisFileRepository files;
    @Mock
    ParseErrorRepository parseErrors;
    @Mock
    FileStorage storage;
    @Mock
    IngestionStore store;
    @Mock
    AnalysisJobs jobs;

    private AnalysisService service(boolean publicMode) {
        var properties = new TraceflowProperties("./target/x", publicMode, null, null, null, null, null);
        return new AnalysisService(analyses, files, parseErrors, storage, store, jobs, properties);
    }

    @Test
    void refusesPersistentImportsInPublicMode() {
        var upload = new MockMultipartFile("files", "a.log", "text/plain", "x".getBytes());
        assertThatThrownBy(() -> service(true).importFiles(List.of(upload), null))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("public mode");
        verifyNoInteractions(storage, jobs);
    }

    @Test
    void enforcesTheMaximumNumberOfFiles() {
        List<MultipartFile> uploads = Collections.nCopies(11, new MockMultipartFile("files", "a.log", "text/plain", "x".getBytes()));
        assertThatThrownBy(() -> service(false).importFiles(uploads, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Too many files: at most 10 files per import");
        verify(analyses, never()).save(any());
    }

    @Test
    void requiresAtLeastOneFile() {
        assertThatThrownBy(() -> service(false).importFiles(List.of(), null)).hasMessage("No files were uploaded");
    }

    @Test
    void sanitizesFileNamesAgainstPathTraversalAndControlCharacters() {
        assertThat(AnalysisService.sanitizeFileName("../../etc/passwd.log")).isEqualTo("passwd.log");
        assertThat(AnalysisService.sanitizeFileName("C:\\logs\\app.log")).isEqualTo("app.log");
        assertThat(AnalysisService.sanitizeFileName("..hidden.log")).isEqualTo("hidden.log");
        assertThat(AnalysisService.sanitizeFileName("we<ird>\u0007.log")).isEqualTo("we_ird_.log");
        assertThat(AnalysisService.sanitizeFileName(null)).isEqualTo("upload.log");
        assertThat(AnalysisService.extension("Service.JSONL")).isEqualTo("jsonl");
    }

    @Test
    void validatesContentAgainstTheDeclaredFormat() {
        var jsonArray = new FileStorage.StoredFile(10, 1, '[', "[{}]");
        var text = new FileStorage.StoredFile(10, 1, '2', "2026-10-27 INFO x");
        var blank = new FileStorage.StoredFile(10, 3, (char) 0, null);
        AnalysisService.validateContent("a.json", "json", jsonArray);
        AnalysisService.validateContent("a.log", "log", text);
        assertThatThrownBy(() -> AnalysisService.validateContent("a.jsonl", "jsonl", jsonArray))
                .hasMessageContaining("not valid JSON Lines");
        assertThatThrownBy(() -> AnalysisService.validateContent("a.json", "json", text)).hasMessageContaining("not valid JSON");
        assertThatThrownBy(() -> AnalysisService.validateContent("a.log", "log", blank)).hasMessageContaining("no log lines");
    }
}
