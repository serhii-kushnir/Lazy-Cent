package org.lazycent.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.controller.CategoryRestController;
import org.lazycent.exception.BusinessException;
import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.dto.ApiErrorDto;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Глобальний обробник помилок для AJAX/REST endpoints.
 * <p>
 * <b>Чому {@code @Order(HIGHEST_PRECEDENCE)}:</b> {@link GlobalExceptionHandler}
 * має {@code @ExceptionHandler(Exception.class)} без selector-а і без `@Order`.
 * Spring MVC обирає перший advice, який може обробити помилку. Без явного
 * пріоритету {@code GlobalExceptionHandler} (той самий {@code LOWEST_PRECEDENCE})
 * вигравав, і REST-відповідь перетворювалася на HTML-редірект.
 */
@RestControllerAdvice(assignableTypes = CategoryRestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class RestApiExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorDto> handleNotFound(EntityNotFoundException ex,
                                                      HttpServletRequest request) {
        log.warn("REST 404 на {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorDto.of(404, "Not Found", ex.getUserMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorDto> handleAccessDenied(AccessDeniedException ex,
                                                          HttpServletRequest request) {
        log.warn("REST 403 на {}: {}", request.getRequestURI(), ex.getMessage());
        String message = ex.getMessage() != null ? ex.getMessage() : msg("error.access.denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorDto.of(403, "Forbidden", message, request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> handleValidation(MethodArgumentNotValidException ex,
                                                        HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        log.warn("REST 400 (Bean Validation) на {}: {} полів з помилками",
                request.getRequestURI(), fieldErrors.size());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorDto.withFieldErrors(
                        400, "Bad Request", msg("error.form.invalid"),
                        request.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorDto> handleBusinessValidation(ValidationException ex,
                                                                HttpServletRequest request) {
        log.warn("REST 400 (business) на {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorDto.of(400, "Bad Request", ex.getUserMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorDto> handleBusiness(BusinessException ex,
                                                      HttpServletRequest request) {
        log.warn("REST 400 (business) на {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorDto.of(400, "Bad Request", ex.getUserMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorDto> handleDataIntegrity(DataIntegrityViolationException ex,
                                                           HttpServletRequest request) {
        log.warn("REST 409 на {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorDto.of(409, "Conflict", msg("error.category.inUse"), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleGeneric(Exception ex,
                                                     HttpServletRequest request) {
        log.error("REST 500 на {}: ", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorDto.of(500, "Internal Server Error",
                        msg("error.technical"), request.getRequestURI()));
    }

    private String msg(String key) {
        try {
            return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return key;
        }
    }
}