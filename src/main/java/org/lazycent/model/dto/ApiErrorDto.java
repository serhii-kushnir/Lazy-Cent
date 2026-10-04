package org.lazycent.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Уніфікований формат помилки для всіх REST/AJAX endpoints.
 * <p>
 * {@code timestamp} зберігається як ISO-8601 рядок, а не {@code LocalDateTime},
 * щоб DTO можна було серіалізувати будь-яким {@code ObjectMapper} без
 * обов'язкового {@code jackson-datatype-jsr310}. У продакшені Spring Boot
 * реєструє модуль автоматично, але в unit-тестах (де створюють
 * {@code new ObjectMapper()}) це не так — рядок уникає проблеми.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorDto(
        int status,
        String error,
        String message,
        String path,
        String timestamp,
        Map<String, String> fieldErrors
) {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static ApiErrorDto of(int status, String error, String message, String path) {
        return new ApiErrorDto(status, error, message, path,
                LocalDateTime.now().format(ISO), null);
    }

    public static ApiErrorDto withFieldErrors(int status, String error, String message,
                                              String path, Map<String, String> fieldErrors) {
        return new ApiErrorDto(status, error, message, path,
                LocalDateTime.now().format(ISO), fieldErrors);
    }
}