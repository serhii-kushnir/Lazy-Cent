package org.lazycent.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void constructor_setsMessageAndUserMessage() {
        BusinessException ex = new BusinessException("user message");
        assertThat(ex.getMessage()).isEqualTo("user message");
        assertThat(ex.getUserMessage()).isEqualTo("user message");
    }

    @Test
    void constructorWithCause_setsCause() {
        RuntimeException cause = new RuntimeException("root");
        BusinessException ex = new BusinessException("user message", cause);
        assertThat(ex.getMessage()).isEqualTo("user message");
        assertThat(ex.getUserMessage()).isEqualTo("user message");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void isRuntimeException() {
        assertThat(new BusinessException("msg"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void userMessage_isIndependentFromMessage() {
        BusinessException ex = new BusinessException("msg", new RuntimeException("cause"));
        assertThat(ex.getUserMessage()).isEqualTo("msg");
    }
}