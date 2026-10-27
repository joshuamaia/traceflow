package dev.traceflow.analysis.infrastructure;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import dev.traceflow.common.security.SensitiveDataMasker;
import dev.traceflow.common.web.BadRequestException;
import dev.traceflow.config.TraceflowProperties;
import org.springframework.stereotype.Component;

/**
 * Local file storage for uploaded and generated log files.
 * <ul>
 *     <li>Files are stored under {@code <storage-dir>/<analysisId>/<generated name>}; user supplied names are never
 *     used to build paths and every resolved path is checked to stay inside the storage root (path traversal).</li>
 *     <li>Content is masked line by line while it is written, so secrets are never persisted.</li>
 * </ul>
 */
@Component
public class FileStorage {

    public record StoredFile(long sizeBytes, long lines, char firstNonWhitespace, String firstNonBlankLine) {
    }

    public record LinesPage(int fromLine, List<String> lines, boolean hasMore) {
    }

    private final Path root;

    public FileStorage(TraceflowProperties properties) {
        this.root = Paths.get(properties.storageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage directory " + root, e);
        }
    }

    /**
     * Copies the stream to storage, masking sensitive data. Rejects binary content and enforces the size limit.
     */
    public StoredFile storeMasked(UUID analysisId, String storedName, InputStream input, long maxBytes)
            throws IOException {
        Path target = resolve(analysisId, storedName);
        Files.createDirectories(target.getParent());
        long lines = 0;
        long bytesRead = 0;
        char first = 0;
        String firstLine = null;
        var counting = new CountingInputStream(input);
        try (BufferedReader reader = reader(counting);
             BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.indexOf('\u0000') >= 0) {
                    throw new BadRequestException("Binary content is not supported");
                }
                bytesRead = counting.count();
                if (bytesRead > maxBytes) {
                    throw new BadRequestException("File exceeds the maximum size of " + (maxBytes / (1024 * 1024)) + " MB");
                }
                if (firstLine == null && !line.isBlank()) {
                    String stripped = line.strip();
                    if (stripped.charAt(0) == '﻿') {
                        stripped = stripped.substring(1).strip();
                    }
                    if (!stripped.isEmpty()) {
                        firstLine = stripped.length() > 500 ? stripped.substring(0, 500) : stripped;
                        first = stripped.charAt(0);
                    }
                }
                writer.write(SensitiveDataMasker.mask(line));
                writer.write('\n');
                lines++;
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        return new StoredFile(Files.size(target), lines, first, firstLine);
    }

    public BufferedWriter newWriter(UUID analysisId, String storedName) throws IOException {
        Path target = resolve(analysisId, storedName);
        Files.createDirectories(target.getParent());
        return Files.newBufferedWriter(target, StandardCharsets.UTF_8);
    }

    public InputStream open(UUID analysisId, String storedName) throws IOException {
        return Files.newInputStream(resolve(analysisId, storedName));
    }

    public long size(UUID analysisId, String storedName) throws IOException {
        return Files.size(resolve(analysisId, storedName));
    }

    public boolean exists(UUID analysisId, String storedName) {
        return Files.exists(resolve(analysisId, storedName));
    }

    /** Reads a window of lines (1-based) without loading the whole file. */
    public LinesPage readLines(UUID analysisId, String storedName, int fromLine, int limit) throws IOException {
        int start = Math.max(1, fromLine);
        List<String> result = new ArrayList<>(Math.min(limit, 2000));
        boolean hasMore = false;
        try (BufferedReader reader = reader(open(analysisId, storedName))) {
            String line;
            int n = 0;
            while ((line = reader.readLine()) != null) {
                n++;
                if (n < start) {
                    continue;
                }
                if (result.size() >= limit) {
                    hasMore = true;
                    break;
                }
                result.add(line);
            }
        }
        return new LinesPage(start, result, hasMore);
    }

    public void deleteAnalysis(UUID analysisId) {
        Path dir = root.resolve(analysisId.toString()).normalize();
        if (!dir.startsWith(root) || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort, retention will retry
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }

    public static BufferedReader reader(InputStream in) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        return new BufferedReader(new InputStreamReader(in, decoder), 64 * 1024);
    }

    private Path resolve(UUID analysisId, String storedName) {
        if (storedName == null || !storedName.matches("[A-Za-z0-9._-]{1,128}") || storedName.contains("..")) {
            throw new BadRequestException("Invalid stored file name");
        }
        Path path = root.resolve(analysisId.toString()).resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            throw new BadRequestException("Invalid file path");
        }
        return path;
    }
}
