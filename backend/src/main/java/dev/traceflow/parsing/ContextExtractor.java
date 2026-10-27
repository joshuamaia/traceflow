package dev.traceflow.parsing;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts correlation context and explicitly logged measurements from free text, e.g.
 * {@code correlationId=abc123}, {@code traceId: 4bf9...}, {@code duration=120ms}, {@code status=500}.
 * Only explicit key/value evidence is used; nothing is guessed from arbitrary numbers.
 */
public final class ContextExtractor {

    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)\\b(x-correlation-id|correlation[-_.]?id|correlationid|cid|x-request-id|request[-_.]?id|"
                    + "trace[-_.]?id|span[-_.]?id|parent[-_.]?span[-_.]?id|message[-_.]?id|"
                    + "duration[-_.]?ms|duration|elapsed[-_.]?ms|latency[-_.]?ms|took|"
                    + "http[-_.]?status|status[-_.]?code|status)\\s*[=:]\\s*\"?([\\w\\-.:/]+?)\"?(?=[\\s,;\\]\\)}]|$)");

    private static final Pattern DURATION_PHRASE = Pattern.compile(
            "(?i)(?:\\b(?:completed|finished|done|processed|responded|returned)\\b[^0-9]{0,20}?\\bin|\\btook)\\s+"
                    + "(\\d+(?:\\.\\d+)?)\\s*(ms|s)\\b");

    private static final Pattern DURATION_VALUE = Pattern.compile("(?i)^(\\d+(?:\\.\\d+)?)(ms|s)?$");

    private ContextExtractor() {
    }

    public static void apply(String text, ParsedEvent event) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher m = KEY_VALUE.matcher(text);
        while (m.find()) {
            String key = m.group(1).toLowerCase(Locale.ROOT).replaceAll("[-_.]", "");
            String value = m.group(2);
            switch (key) {
                case "xcorrelationid", "correlationid", "cid" -> setIfAbsent(value, event.getCorrelationId(),
                        event::setCorrelationId);
                case "xrequestid", "requestid" -> {
                    if (event.getCorrelationId() == null) {
                        event.setCorrelationId(value);
                    }
                }
                case "traceid" -> setIfAbsent(value, event.getTraceId(), event::setTraceId);
                case "spanid" -> setIfAbsent(value, event.getSpanId(), event::setSpanId);
                case "parentspanid" -> setIfAbsent(value, event.getParentSpanId(), event::setParentSpanId);
                case "messageid" -> setIfAbsent(value, event.getMessageId(), event::setMessageId);
                case "durationms", "elapsedms", "latencyms" -> {
                    if (event.getDurationMs() == null) {
                        parseDuration(value, true).ifPresent(event::setDurationMs);
                    }
                }
                case "duration", "took" -> {
                    if (event.getDurationMs() == null) {
                        parseDuration(value, false).ifPresent(event::setDurationMs);
                    }
                }
                case "httpstatus", "statuscode", "status" -> {
                    if (event.getHttpStatus() == null) {
                        parseHttpStatus(value).ifPresent(event::setHttpStatus);
                    }
                }
                default -> {
                    // not reachable - the regex only captures the keys above
                }
            }
        }
        if (event.getDurationMs() == null) {
            Matcher d = DURATION_PHRASE.matcher(text);
            if (d.find()) {
                double amount = Double.parseDouble(d.group(1));
                event.setDurationMs(Math.round("s".equalsIgnoreCase(d.group(2)) ? amount * 1000 : amount));
            }
        }
    }

    /**
     * @param assumeMillis when the value has no unit: true treats it as milliseconds, false requires a unit
     */
    static java.util.Optional<Long> parseDuration(String value, boolean assumeMillis) {
        Matcher m = DURATION_VALUE.matcher(value.trim());
        if (!m.matches()) {
            return java.util.Optional.empty();
        }
        double amount = Double.parseDouble(m.group(1));
        String unit = m.group(2);
        if (unit == null) {
            return assumeMillis ? java.util.Optional.of(Math.round(amount)) : java.util.Optional.empty();
        }
        return java.util.Optional.of(Math.round(unit.equalsIgnoreCase("s") ? amount * 1000 : amount));
    }

    static java.util.Optional<Integer> parseHttpStatus(String value) {
        if (value == null || !value.matches("\\d{3}")) {
            return java.util.Optional.empty();
        }
        int status = Integer.parseInt(value);
        return status >= 100 && status <= 599 ? java.util.Optional.of(status) : java.util.Optional.empty();
    }

    private static void setIfAbsent(String value, String current, java.util.function.Consumer<String> setter) {
        if (current == null && value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }
}
