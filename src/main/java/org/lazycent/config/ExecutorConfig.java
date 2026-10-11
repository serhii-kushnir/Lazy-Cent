package org.lazycent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Конфігурація пулів executor'ів.
 *
 * <p><b>Винесено з {@link AsyncConfig} для розриву циклічної залежності:</b>
 * {@code AsyncConfig} інжектить {@code mailExecutor}, але сам же його
 * створює. Spring не може розв'язати таку залежність без {@code @Lazy}.
 *
 * <p>Розділення на два класи — стандартне рішення: один клас створює бін,
 * інший його використовує. Тут — створення, у {@code AsyncConfig} — використання.
 */
@Configuration
public class ExecutorConfig {

    /**
     * Пул для надсилання листів.
     *
     * <p>Параметри:
     * <ul>
     *   <li>{@code corePoolSize=2} — два листи одночасно;</li>
     *   <li>{@code maxPoolSize=5} — максимум 5 під навантаженням;</li>
     *   <li>{@code queueCapacity=100} — до 100 листів у черзі;</li>
     *   <li>при переповненні — {@code CallerRunsPolicy} (див. нижче).</li>
     * </ul>
     *
     * <p><b>Чому {@code CallerRunsPolicy}:</b> якщо пул забитий і черга
     * повна, завдання виконується в потоці, що викликав (HTTP-запит
     * користувача). Це уповільнює запит, але гарантує, що лист не буде
     * загублено. Для password-reset це критично.
     */
    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}