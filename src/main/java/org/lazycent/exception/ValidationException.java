package org.lazycent.exception;

/**
 * Виняток для помилок валідації бізнес-логіки (наприклад, дублікат категорії).
 */
public class ValidationException extends BusinessException {
    public ValidationException(String message) {
        super(message);
    }
}