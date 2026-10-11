package org.lazycent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executor;

/**
 * Конфігурація {@code @Async} та {@code @Scheduled}.
 *
 * <p>Сам бін {@code mailExecutor} створюється у {@link ExecutorConfig} —
 * це дозволяє інжектити його через конструктор без циклічної залежності
 * (один клас створює, інший споживає).
 */
@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Той самий бін, що оголошений у {@link ExecutorConfig#mailExecutor()}.
     * Інжектиться через конструктор — це єдиний надійний спосіб
     * (без {@code @Lazy} на полях і без CGLIB-proxy трюків).
     */
    private final Executor mailExecutor;

    public AsyncConfig(@Qualifier("mailExecutor") Executor mailExecutor) {
        this.mailExecutor = mailExecutor;
    }

    /**
     * Executor за замовчуванням для всіх {@code @Async}-методів без
     * явного {@code @Async("...")}. Використовуємо той самий
     * {@code mailExecutor}, щоб не тримати два пули для тієї самої задачі.
     */
    @Override
    public Executor getAsyncExecutor() {
        return mailExecutor;
    }

    /**
     * Обробник «зникаючих» винятків із {@code @Async}-методів, які
     * повертають {@code void}.
     *
     * <p><b>Чому потрібен:</b> без нього Spring використовує
     * {@link org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler},
     * який логує виняток, але НЕ дає можливості додати структурований
     * контекст, інкрементувати метрику або зробити retry.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("Помилка у @Async-методі {}.{}(): {}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName(),
                    ex.getMessage(),
                    ex);

            // TODO: якщо з'явиться MeterRegistry — інкрементувати
            // meterRegistry.counter("lazycent.async.errors",
            //         "method", method.getName()).increment();
        };
    }
}