package org.lazycent.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit-тести {@link AsyncConfig}.
 *
 * <p>Не перевіряємо singleton-ність бінів — це справа Spring-контексту
 * (див. {@link AsyncConfigSpringTest}). Тут тестуємо логіку: кастомний
 * {@code AsyncUncaughtExceptionHandler} і те, що він не кидає виняток.
 */
class AsyncConfigTest {

    private final AsyncConfig config = new AsyncConfig();

    @Test
    void getAsyncUncaughtExceptionHandler_returnsCustomHandler() {
        AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();
        assertThat(handler).isNotNull();
    }

    @Test
    void handler_doesNotThrow_whenExceptionIsThrown() throws NoSuchMethodException {
        AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();
        Method method = AsyncConfigTest.class.getDeclaredMethod("dummyMethod");

        // Обробник має проковтнути виняток собі в лог, а не кинути його далі
        assertThatCode(() ->
                handler.handleUncaughtException(
                        new RuntimeException("SMTP timeout"),
                        method,
                        new Object[]{"test@example.com"}))
                .doesNotThrowAnyException();
    }

    @Test
    void mailExecutor_createsNonNullExecutor() {
        // Просто перевіряємо, що бін створюється без помилок
        assertThat(config.mailExecutor()).isNotNull();
        assertThat(config.getAsyncExecutor()).isNotNull();
    }

    @SuppressWarnings("unused")
    private void dummyMethod() {}
}