package org.lazycent.exception;

public class EntityNotFoundException extends BusinessException {
    public EntityNotFoundException(String entityName, Object id) {
        super(String.format("%s з ID %s не знайдено", entityName, id));
    }

    public EntityNotFoundException(String message) {
        super(message);
    }
}