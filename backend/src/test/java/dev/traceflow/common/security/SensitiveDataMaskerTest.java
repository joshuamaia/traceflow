package dev.traceflow.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Authorization: Bearer abc.def.ghi|Authorization: Bearer [MASKED]",
            "authorization=Basic dXNlcjpwYXNz|authorization=Basic [MASKED]",
            "login password=hunter2 ok|login password=[MASKED] ok",
            "{\"password\":\"s3cret\",\"user\":\"bob\"}|{\"password\":\"[MASKED]\",\"user\":\"bob\"}",
            "api_key: 12345 next|api_key: [MASKED] next",
            "Cookie: JSESSIONID=abc; theme=dark|Cookie: [MASKED]",
            "jdbc:postgresql://admin:topsecret@db:5432/x|jdbc:postgresql://admin:[MASKED]@db:5432/x"
    })
    void masksSecrets(String input, String expected) {
        assertThat(SensitiveDataMasker.mask(input)).isEqualTo(expected);
    }

    @Test
    void masksJwtAnywhere() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        assertThat(SensitiveDataMasker.mask("token " + jwt + " used")).isEqualTo("token [MASKED_JWT] used");
    }

    @Test
    void keepsNormalContentAndLineStructure() {
        String text = "2026-10-27 10:15:30 INFO [order-service] [correlationId=abc123] Starting order processing";
        assertThat(SensitiveDataMasker.mask(text)).isEqualTo(text);
        assertThat(SensitiveDataMasker.mask(null)).isNull();
    }
}
