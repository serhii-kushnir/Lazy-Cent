package org.lazycent.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Перевіряє, що {@code mailExecutor} — singleton у Spring-контексті,
 * і що {@link AsyncConfig} інжектить саме його.
 *
 * <p>Після розділення на {@link ExecutorConfig} + {@link AsyncConfig}
 * циклічна залежність зникла, і обидва класи отримують той самий бін.
 */
@SpringBootTest
@ActiveProfiles("test")
class AsyncConfigSpringTest {

    @Autowired @Qualifier("mailExecutor")
    private Executor mailExecutor1;

    @Autowired @Qualifier("mailExecutor")
    private Executor mailExecutor2;

    @Autowired
    private AsyncConfig asyncConfig;

    @Test
    void mailExecutor_isSingleton() {
        assertThat(mailExecutor1).isSameAs(mailExecutor2);
    }

    @Test
    void asyncConfig_injectsSameMailExecutor() {
        assertThat(asyncConfig.getAsyncExecutor())
                .as("AsyncConfig інжектить той самий бін, що й @Qualifier(\"mailExecutor\")")
                .isSameAs(mailExecutor1);
    }
}