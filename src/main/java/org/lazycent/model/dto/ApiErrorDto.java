package org.lazycent.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Уніфікований формат помилки для всіх REST/AJAX endpoints.
 *
 * <p>{@code timestamp} — ISO-8601 рядок, щоб DTO серіалізувався будь-яким
 * {@code ObjectMapper} без JSR310-модуля.
 *
 * <p>{@code traceId} — з MDC (див. {@code TraceIdFilter}). Дозволяє
 * зіставити клієнтську помилку з конкретним запитом у логах.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorDto(
        int status,
        String error,
        String message,
        String path,
        String timestamp,
        String traceId,
        Map<String, String> fieldErrors
) {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static ApiErrorDto of(int status, String error, String message, String path) {
        return new ApiErrorDto(status, error, message, path,
                LocalDateTime.now().format(ISO), currentTraceId(), null);
    }

    public static ApiErrorDto withFieldErrors(int status, String error, String message,
                                              String path, Map<String, String> fieldErrors) {
        return new ApiErrorDto(status, error, message, path,
                LocalDateTime.now().format(ISO), currentTraceId(), fieldErrors);
    }

    private static String currentTraceId() {
        String id = MDC.get("traceId");
        return id != null ? id : "-";
    }
}