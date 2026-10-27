package dev.traceflow.parsing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Builds a stable fingerprint used to group similar errors: exception class + normalized message (variable parts
 * such as ids, numbers and quoted values removed) + the first application stack frame.
 */
public final class Fingerprints {

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern HEX = Pattern.compile("\\b[0-9a-fA-F]{8,}\\b");
    private static final Pattern QUOTED = Pattern.compile("'[^']*'|\"[^\"]*\"");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
    private static final Pattern ID_TOKEN = Pattern.compile("\\b[\\w]+[-_]\\d[\\w-]*\\b");

    private Fingerprints() {
    }

    public static String of(String exceptionClass, String message, String stackTrace) {
        String base = (exceptionClass == null ? "log" : exceptionClass) + "|" + normalizeMessage(message) + "|"
                + StackTraceAnalyzer.firstApplicationFrame(stackTrace).orElse("");
        return sha1(base).substring(0, 16);
    }

    public static String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }
        String first = message.lines().findFirst().orElse("");
        String normalized = UUID.matcher(first).replaceAll("<uuid>");
        normalized = QUOTED.matcher(normalized).replaceAll("<str>");
        normalized = HEX.matcher(normalized).replaceAll("<hex>");
        normalized = ID_TOKEN.matcher(normalized).replaceAll("<id>");
        normalized = NUMBER.matcher(normalized).replaceAll("<n>");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    private static String sha1(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
