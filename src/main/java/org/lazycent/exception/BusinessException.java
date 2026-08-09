package org.lazycent.exception;

/**
 * Базовий виняток для всіх бізнес-помилок.
 * Містить зрозуміле для користувача повідомлення.
 */
public class BusinessException extends RuntimeException {
    private final String userMessage;

    public BusinessException(String userMessage) {
        super(userMessage);
        this.userMessage = userMessage;
    }

    public BusinessException(String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}