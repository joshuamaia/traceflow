package dev.traceflow.parsing.parser;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.traceflow.parsing.ContextExtractor;
import dev.traceflow.parsing.LogLevels;
import dev.traceflow.parsing.LogLineParser;
import dev.traceflow.parsing.ParseContext;
import dev.traceflow.parsing.ParsedEvent;
import dev.traceflow.parsing.Timestamps;

/**
 * Generic "timestamp + level + bracketed sections + message" layouts, e.g.
 *
 * <pre>
 * 2026-10-27 10:15:30 INFO [order-service] [correlationId=abc123] Starting order processing
 * 2026-10-27 10:15:30.123 [http-nio-8080-exec-1] ERROR dev.traceflow.OrderService - Payment failed
 * [2026-10-27T10:15:30Z] WARN [payment-service,4bf92f3577b34da6,00f067aa0ba902b7] Slow response
 * </pre>
 * <p>
 * Bracketed sections are classified as key/value context, Sleuth style {@code [app,traceId,spanId]}, thread names or
 * service names. A level token is mandatory to avoid false positives.
 */
public final class ClassicTextLogParser implements LogLineParser {

    public static final String NAME = "TEXT";

    private static final Pattern LINE = Pattern.compile("^\\[?(?<ts>" + Timestamps.PATTERN + ")\\]?\\s+(?<rest>.+)$");
    private static final Pattern THREAD_LIKE = Pattern.compile(
            "(?i)^(main|.*(exec|thread|pool|worker|scheduling|task|nio|reactor|forkjoin|timer|async|consumer|"
                    + "listener|executor|boundedelastic|parallel|virtual).*|.*-\\d+)$");
    private static final Pattern LOGGER_LIKE = Pattern.compile("^[a-zA-Z_$][\\w$]*(\\.[\\w$]+)+$|^[A-Z][\\w$]+$");
    private static final Pattern SERVICE_LIKE = Pattern.compile("^[a-zA-Z][\\w.-]{1,127}$");
    private static final Pattern PAIR = Pattern.compile("([\\w.-]+)\\s*[=:]\\s*([^,;\\s]+)");
    private static final Pattern HEX_ID = Pattern.compile("[0-9a-fA-F]{8,32}");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public boolean accepts(String line) {
        return !line.isEmpty() && (Character.isDigit(line.charAt(0)) || line.charAt(0) == '[');
    }

    @Override
    public ParsedEvent parse(String line, ParseContext context) {
        Matcher m = LINE.matcher(line);
        if (!m.matches()) {
            return null;
        }
        String rest = m.group("rest");
        var event = new ParsedEvent(NAME, context.lineNumber(), line);
        String level = null;
        int i = 0;
        int n = rest.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(rest.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            char c = rest.charAt(i);
            if (c == '[') {
                int close = rest.indexOf(']', i);
                if (close < 0) {
                    break;
                }
                classifyBracket(rest.substring(i + 1, close).trim(), event, level != null);
                i = close + 1;
                continue;
            }
            int end = i;
            while (end < n && !Character.isWhitespace(rest.charAt(end))) {
                end++;
            }
            String token = rest.substring(i, end);
            if (level == null && LogLevels.normalize(token) != null) {
                level = LogLevels.normalize(token);
                i = end;
                continue;
            }
            if (level == null && (token.matches("\\d+") || token.equals("---"))) {
                // process id / separators before the level
                i = end;
                continue;
            }
            if (token.equals("-") || token.equals(":") || token.equals("|")) {
                i = end;
                break;
            }
            String candidate = token.endsWith(":") ? token.substring(0, token.length() - 1) : token;
            if (level != null && event.getLogger() == null && LOGGER_LIKE.matcher(candidate).matches()
                    && (token.endsWith(":") || nextTokenIsSeparator(rest, end))) {
                event.setLogger(candidate);
                i = end;
                continue;
            }
            break;
        }
        if (level == null) {
            return null;
        }
        event.setLevel(level);
        String ts = m.group("ts");
        event.setTimestampRaw(ts);
        Timestamps.parse(ts, context.defaultZone()).ifPresentOrElse(event::setTimestamp,
                () -> event.setTimestampInvalid(true));
        String message = i < n ? rest.substring(i).trim() : "";
        if (message.startsWith("- ") || message.startsWith(": ")) {
            message = message.substring(2);
        }
        event.setMessage(message);
        if (event.getService() == null && context.defaultService() != null) {
            event.setService(context.defaultService());
            event.setServiceFromFileName(true);
        }
        ContextExtractor.apply(message, event);
        return event;
    }

    private static boolean nextTokenIsSeparator(String rest, int from) {
        int i = from;
        while (i < rest.length() && Character.isWhitespace(rest.charAt(i))) {
            i++;
        }
        return i < rest.length() && (rest.charAt(i) == '-' || rest.charAt(i) == ':' || rest.charAt(i) == '|')
                && (i + 1 >= rest.length() || Character.isWhitespace(rest.charAt(i + 1)));
    }

    private static void classifyBracket(String content, ParsedEvent event, boolean afterLevel) {
        if (content.isEmpty()) {
            return;
        }
        if (content.contains("=") || (content.contains(":") && PAIR.matcher(content).find()
                && !content.matches(".*\\d{2}:\\d{2}.*"))) {
            Matcher pair = PAIR.matcher(content);
            while (pair.find()) {
                String key = pair.group(1);
                String value = pair.group(2);
                String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[-_.]", "");
                switch (normalized) {
                    case "service", "app", "application", "servicename" -> event.setService(value);
                    case "thread" -> event.setThread(value);
                    default -> {
                        int before = countIds(event);
                        ContextExtractor.apply(key + "=" + value, event);
                        if (countIds(event) == before) {
                            event.getAttributes().put(key, value);
                        }
                    }
                }
            }
            return;
        }
        if (content.contains(",")) {
            String[] parts = content.split(",");
            if (parts.length >= 2 && HEX_ID.matcher(parts[1].trim()).matches()) {
                if (!parts[0].isBlank() && event.getService() == null) {
                    event.setService(parts[0].trim());
                }
                event.setTraceId(parts[1].trim());
                if (parts.length >= 3 && HEX_ID.matcher(parts[2].trim()).matches()) {
                    event.setSpanId(parts[2].trim());
                }
                return;
            }
        }
        if (event.getThread() == null && (THREAD_LIKE.matcher(content).matches() || !afterLevel)) {
            event.setThread(content);
            return;
        }
        if (event.getService() == null && SERVICE_LIKE.matcher(content).matches()) {
            event.setService(content);
            return;
        }
        event.getAttributes().put("section" + (event.getAttributes().size() + 1), content);
    }

    private static int countIds(ParsedEvent e) {
        int count = 0;
        for (Object v : new Object[]{e.getCorrelationId(), e.getTraceId(), e.getSpanId(), e.getParentSpanId(),
                e.getMessageId(), e.getDurationMs(), e.getHttpStatus()}) {
            if (v != null) {
                count++;
            }
        }
        return count;
    }
}
