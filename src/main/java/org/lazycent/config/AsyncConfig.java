package org.lazycent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Пул для надсилання листів.
     *
     * <p>Параметри:
     * <ul>
     *   <li>{@code corePoolSize=2} — два листи одночасно;</li>
     *   <li>{@code maxPoolSize=5} — максимум 5 під навантаженням;</li>
     *   <li>{@code queueCapacity=100} — до 100 листів у черзі;</li>
     *   <li>при переповненні черги — {@code CallerRunsPolicy} (див. нижче).</li>
     * </ul>
     *
     * <p><b>Чому {@code CallerRunsPolicy}:</b> якщо пул забитий і черга
     * повна, завдання виконується в потоці, що викликав (тобто в
     * HTTP-запиті користувача). Це уповільнює запит, але гарантує,
     * що лист не буде загублено. Альтернатива ({@code AbortPolicy}) —
     * загублений лист, що для password-reset неприйнятно.
     */
    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Executor за замовчуванням для всіх {@code @Async}-методів без
     * явного {@code @Async("...")}. Використовуємо той самий пул —
     * окремий бін для уніфікації.
     */
    @Override
    public Executor getAsyncExecutor() {
        return mailExecutor();
    }

    /**
     * Обробник «зникаючих» винятків із {@code @Async}-методів, які
     * повертають {@code void}.
     *
     * <p><b>Чому потрібен:</b> без нього Spring використовує
     * {@link SimpleAsyncUncaughtExceptionHandler}, який логує виняток,
     * але НЕ дає можливості:
     * <ul>
     *   <li>додати структурований контекст (який метод, які параметри);</li>
     *   <li>інкрементувати метрику (для Prometheus / Micrometer);</li>
     *   <li>зробити retry або fallback.</li>
     * </ul>
     *
     * <p>Зараз — тільки розширене логування. Місце для майбутнього
     * {@code meterRegistry.counter("async.errors", "method", method.getName()).increment()}.
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

            // TODO: retry для idempotent-операцій (надсилання листів)
            // через spring-retry (@Retryable на MailService).
        };
    }
}