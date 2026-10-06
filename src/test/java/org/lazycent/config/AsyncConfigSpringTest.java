package org.lazycent.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Перевіряє, що {@code mailExecutor} — singleton у Spring-контексті.
 *
 * <p>Це має сенс ТІЛЬКИ з контекстом: у чистому unit-тесті виклик
 * {@code mailExecutor()} створює новий об'єкт щоразу, бо немає проксі
 * {@code @Bean}. У контексті Spring гарантує один екземпляр.
 */
@SpringBootTest
@ActiveProfiles("test")
class AsyncConfigSpringTest {

    @Autowired @Qualifier("mailExecutor")
    private Executor mailExecutor1;

    @Autowired @Qualifier("mailExecutor")
    private Executor mailExecutor2;

    @Test
    void mailExecutor_isSingleton() {
        assertThat(mailExecutor1).isSameAs(mailExecutor2);
    }
}