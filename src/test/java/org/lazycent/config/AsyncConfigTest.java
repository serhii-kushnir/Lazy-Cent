package org.lazycent.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Unit-тести {@link AsyncConfig}.
 *
 * <p>Оскільки {@code AsyncConfig} тепер інжектить {@code mailExecutor}
 * через конструктор, у тесті передаємо mock — це ізолює логіку
 * (обробник винятків) від Spring-контексту.
 *
 * <p>Singleton-ність бінів перевіряється у {@link AsyncConfigSpringTest}.
 */
class AsyncConfigTest {

    private final Executor mockExecutor = mock(Executor.class);
    private final AsyncConfig config = new AsyncConfig(mockExecutor);

    @Test
    void getAsyncExecutor_returnsInjectedExecutor() {
        assertThat(config.getAsyncExecutor()).isSameAs(mockExecutor);
    }

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

    @SuppressWarnings("unused")
    private void dummyMethod() {}
}