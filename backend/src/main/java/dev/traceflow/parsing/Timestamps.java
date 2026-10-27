package dev.traceflow.parsing;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lenient timestamp parsing for the formats commonly produced by Java logging frameworks. Timestamps without a zone
 * are interpreted in the configured default zone. Implausible values (before 1970 or after 2100) are rejected.
 */
public final class Timestamps {

    /** Regex fragment matching the supported textual timestamp layouts. */
    public static final String PATTERN =
            "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:\\s?(?:Z|[+-]\\d{2}:?\\d{2}))?";

    private static final Pattern COMPACT_OFFSET = Pattern.compile("([+-]\\d{2})(\\d{2})$");
    private static final Instant MIN = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX = Instant.parse("2100-01-01T00:00:00Z");

    private Timestamps() {
    }

    public static Optional<Instant> parse(String raw, ZoneId defaultZone) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim().replace(',', '.');
        if (value.length() > 10 && value.charAt(10) == ' ') {
            value = value.substring(0, 10) + 'T' + value.substring(11);
        }
        value = value.replace(" Z", "Z").replaceAll("\\s([+-]\\d{2})", "$1");
        value = COMPACT_OFFSET.matcher(value).replaceFirst("$1:$2");
        Instant instant;
        try {
            instant = OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException offsetFailure) {
            try {
                instant = LocalDateTime.parse(value).atZone(defaultZone).toInstant();
            } catch (DateTimeParseException localFailure) {
                return Optional.empty();
            }
        }
        return plausible(instant);
    }

    /** Interprets epoch numbers: values above 10^11 are milliseconds, otherwise (fractional) seconds. */
    public static Optional<Instant> fromEpoch(double value) {
        if (Double.isNaN(value) || value <= 0) {
            return Optional.empty();
        }
        Instant instant = value > 1e11
                ? Instant.ofEpochMilli((long) value)
                : Instant.ofEpochMilli((long) (value * 1000));
        return plausible(instant);
    }

    private static Optional<Instant> plausible(Instant instant) {
        if (instant.isBefore(MIN) || instant.isAfter(MAX)) {
            return Optional.empty();
        }
        return Optional.of(instant);
    }
}
