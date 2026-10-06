package org.lazycent.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationExceptionTest {

    @Test
    void constructor_setsMessageAndUserMessage() {
        ValidationException ex = new ValidationException("invalid input");
        assertThat(ex.getMessage()).isEqualTo("invalid input");
        assertThat(ex.getUserMessage()).isEqualTo("invalid input");
    }

    @Test
    void isBusinessException() {
        ValidationException ex = new ValidationException("X");
        assertThat(ex).isInstanceOf(BusinessException.class);
    }

    @Test
    void isRuntimeException() {
        ValidationException ex = new ValidationException("X");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}