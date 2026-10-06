package org.lazycent.model.dto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorDtoTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void of_createsDtoWithAllFields() {
        ApiErrorDto dto = ApiErrorDto.of(404, "Not Found", "message", "/path");

        assertThat(dto.status()).isEqualTo(404);
        assertThat(dto.error()).isEqualTo("Not Found");
        assertThat(dto.message()).isEqualTo("message");
        assertThat(dto.path()).isEqualTo("/path");
        assertThat(dto.timestamp()).isNotNull();
        assertThat(dto.fieldErrors()).isNull();
    }

    @Test
    void withFieldErrors_setsFieldErrors() {
        Map<String, String> errors = Map.of("name", "required", "email", "invalid");

        ApiErrorDto dto = ApiErrorDto.withFieldErrors(
                400, "Bad Request", "validation failed", "/register", errors);

        assertThat(dto.status()).isEqualTo(400);
        assertThat(dto.error()).isEqualTo("Bad Request");
        assertThat(dto.fieldErrors()).isEqualTo(errors);
    }

    @Test
    void of_usesTraceIdFromMdc_whenPresent() {
        MDC.put("traceId", "abc123");
        try {
            ApiErrorDto dto = ApiErrorDto.of(500, "Error", "msg", "/path");
            assertThat(dto.traceId()).isEqualTo("abc123");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void of_usesDash_whenTraceIdMissing() {
        MDC.clear();
        ApiErrorDto dto = ApiErrorDto.of(500, "Error", "msg", "/path");
        assertThat(dto.traceId()).isEqualTo("-");
    }

    @Test
    void timestamp_isIso8601() {
        ApiErrorDto dto = ApiErrorDto.of(200, "OK", "msg", "/path");
        assertThat(dto.timestamp())
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?");
    }

    @Test
    void fieldErrors_isNull_whenNotSet() {
        ApiErrorDto dto = ApiErrorDto.of(200, "OK", "msg", "/path");
        assertThat(dto.fieldErrors()).isNull();
    }
}