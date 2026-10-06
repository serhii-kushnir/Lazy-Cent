package org.lazycent.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityNotFoundExceptionTest {

    @Test
    void constructorWithEntityAndId_formatsMessage() {
        EntityNotFoundException ex = new EntityNotFoundException("Категорію", 42L);
        assertThat(ex.getUserMessage())
                .contains("Категорію")
                .contains("42");
    }

    @Test
    void constructorWithEntityAndStringId_formatsMessage() {
        EntityNotFoundException ex = new EntityNotFoundException("День", "2026-10-06");
        assertThat(ex.getUserMessage())
                .contains("День")
                .contains("2026-10-06");
    }

    @Test
    void constructorWithMessage_setsMessage() {
        EntityNotFoundException ex = new EntityNotFoundException("custom message");
        assertThat(ex.getUserMessage()).isEqualTo("custom message");
        assertThat(ex.getMessage()).isEqualTo("custom message");
    }

    @Test
    void isBusinessException() {
        EntityNotFoundException ex = new EntityNotFoundException("X");
        assertThat(ex).isInstanceOf(BusinessException.class);
    }

    @Test
    void isRuntimeException() {
        EntityNotFoundException ex = new EntityNotFoundException("X");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}