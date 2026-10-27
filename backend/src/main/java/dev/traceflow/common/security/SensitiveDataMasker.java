package dev.traceflow.common.security;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Masks credentials and secrets (JWTs, passwords, API keys, cookies, Authorization headers) found in log content.
 * Applied to every line before it is stored on disk or in the database, so unmasked values are never persisted.
 * Masking never changes the number of lines, which keeps line references stable.
 */
public final class SensitiveDataMasker {

    public static final String MASK = "[MASKED]";

    private record Rule(Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            // Authorization headers: "Authorization: Bearer xyz", "authorization=Basic abc"
            new Rule(Pattern.compile("(?i)(\"?(?:proxy-)?authorization\"?\\s*[:=]\\s*\"?)"
                    + "((?:bearer|basic|digest|token)\\s+)?[^\\s\",;}]+"), "$1$2" + MASK),
            // Cookies (request and response headers)
            new Rule(Pattern.compile("(?i)(\"?(?:set-)?cookie\"?\\s*[:=]\\s*\"?)[^\"\\r\\n]+"), "$1" + MASK),
            // JSON Web Tokens anywhere in the line
            new Rule(Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*"), "[MASKED_JWT]"),
            // key=value / "key": "value" secrets
            new Rule(Pattern.compile("(?i)(\"?\\b(?:password|passwd|pwd|secret|client[_-]?secret|api[_-]?key|apikey|"
                    + "access[_-]?token|refresh[_-]?token|id[_-]?token|auth[_-]?token|token|private[_-]?key|x-api-key)\"?"
                    + "\\s*[:=]\\s*\"?)(?!\\[MASKED)[^\\s\",;&}]+"), "$1" + MASK),
            // Credentials embedded in URLs: scheme://user:password@host
            new Rule(Pattern.compile("([a-zA-Z][a-zA-Z0-9+.-]*://[^\\s:/@]+:)[^\\s@/]+@"), "$1" + MASK + "@"));

    private SensitiveDataMasker() {
    }

    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        for (Rule rule : RULES) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return result;
    }
}
