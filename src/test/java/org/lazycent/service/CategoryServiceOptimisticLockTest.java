package org.lazycent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.Category;
import org.lazycent.model.User;
import org.lazycent.repository.CategoryRepository;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration-тест optimistic locking на {@link Category}.
 *
 * <p><b>Чому без {@code @Transactional} на класі:</b> optimistic lock
 * вимагає, щоб «SELECT у вкладці A» і «UPDATE у вкладці B» відбувались
 * у РІЗНИХ транзакціях. З {@code @Transactional} на тесті всі запити
 * виконуються в одній транзакції, і version-check не спрацьовує.
 *
 * <p>Тому кожен {@code repository.find*}/{@code save*} виконується у
 * власній транзакції, а сутності автоматично стають detached після
 * завершення кожної операції. Це саме той сценарій, який буває у
 * реальному застосунку з двома вкладками.
 */
@SpringBootTest
@ActiveProfiles("test")
class CategoryServiceOptimisticLockTest {

    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setUsername("optlock_test_user");
        user.setPassword("encoded");
        user.setRole("USER");
        user.setPreferredCurrency("UAH");
        user = userRepository.save(user);
    }

    @Test
    void updateCategory_throwsOptimisticLock_onConcurrentModification() {
        // 1. Створюємо категорію
        Category created = categoryService.addCategory("Одяг", "EXPENSE", user);
        Long id = created.getId();

        // 2. Завантажуємо «застарілу копію» — як вкладка B
        Category staleCopy = categoryRepository.findById(id).orElseThrow();
        Long initialVersion = staleCopy.getVersion();
        assertThat(initialVersion).isNotNull();

        // 3. Вкладка A оновлює категорію (version++)
        categoryService.updateCategory(id, "Одяг та взуття", user);

        // Перевіряємо, що version у БД справді збільшилась
        Category fresh = categoryRepository.findById(id).orElseThrow();
        assertThat(fresh.getVersion()).isGreaterThan(initialVersion);

        // 4. Вкладка B намагається зберегти застарілу копію → 409
        staleCopy.setName("Продукти");

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(staleCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // 5. У БД залишається версія вкладки A — lost update не стався
        Category after = categoryRepository.findById(id).orElseThrow();
        assertThat(after.getName()).isEqualTo("Одяг та взуття");
    }

    @Test
    void updateCategory_succeeds_whenSequential() {
        // Контрольний тест: послідовні оновлення (без гонки) працюють нормально

        Category created = categoryService.addCategory("Їжа", "EXPENSE", user);
        Long id = created.getId();

        categoryService.updateCategory(id, "Їжа v2", user);
        categoryService.updateCategory(id, "Їжа v3", user);

        Category result = categoryRepository.findById(id).orElseThrow();
        assertThat(result.getName()).isEqualTo("Їжа v3");
        assertThat(result.getVersion()).isGreaterThan(0L);
    }
}