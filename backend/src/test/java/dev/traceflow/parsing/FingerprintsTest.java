package dev.traceflow.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FingerprintsTest {

    private static final String STACK = "x.Y: m\n\tat com.acme.Repo.save(Repo.java:10)";

    @Test
    void groupsMessagesThatOnlyDifferInVariableParts() {
        String a = Fingerprints.of("java.sql.SQLException", "Key (order_number)=(ORD-1A2B3C) already exists, id 42", STACK);
        String b = Fingerprints.of("java.sql.SQLException", "Key (order_number)=(ORD-9F8E7D) already exists, id 7", STACK);
        assertThat(a).isEqualTo(b).hasSize(16);
    }

    @Test
    void separatesDifferentExceptionsAndOrigins() {
        String base = Fingerprints.of("java.sql.SQLException", "duplicate key", STACK);
        assertThat(Fingerprints.of("java.io.IOException", "duplicate key", STACK)).isNotEqualTo(base);
        assertThat(Fingerprints.of("java.sql.SQLException", "duplicate key", "x.Y: m\n\tat com.acme.Other.run(Other.java:1)"))
                .isNotEqualTo(base);
    }

    @Test
    void normalizesIdentifiers() {
        assertThat(Fingerprints.normalizeMessage("user 123e4567-e89b-12d3-a456-426614174000 failed after 3 tries 'x'"))
                .isEqualTo("user <uuid> failed after <n> tries <str>");
    }
}
