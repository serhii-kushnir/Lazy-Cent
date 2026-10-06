package org.lazycent.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.User;
import org.lazycent.repository.CategoryRepository;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Перевіряє, що unique index {@code uk_category_user_name} (V5)
 * дійсно захищає від race condition.
 *
 * <p>У профілі {@code test} Hibernate створює схему через
 * {@code ddl-auto: create-drop} — без Flyway. Unique index створюється
 * Hibernate автоматично, бо в {@code Category} є
 * {@code @Table(uniqueConstraints=...)}.
 *
 * <p>У {@code @BeforeEach} встановлюємо українську локаль, щоб
 * {@code MessageSource} повертав тексти з {@code messages.properties},
 * а не з {@code messages_en.properties}.
 */
@SpringBootTest
@ActiveProfiles("test")
class CategoryServiceUniqueConstraintTest {

    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk-UA"));

        categoryRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setUsername("unique_test_user");
        user.setPassword("encoded");
        user.setRole("USER");
        user.setPreferredCurrency("UAH");
        user = userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void addCategory_throwsValidation_whenNameAlreadyExists() {
        categoryService.addCategory("Їжа", "EXPENSE", user);

        assertThatThrownBy(() -> categoryService.addCategory("Їжа", "EXPENSE", user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("вже існує");
    }

    @Test
    void addCategory_caseInsensitiveDuplicate_isRejected() {
        categoryService.addCategory("Їжа", "EXPENSE", user);

        // Сервісна перевірка findByNameIgnoreCaseAndOwner ловить це до save()
        assertThatThrownBy(() -> categoryService.addCategory("їжа", "EXPENSE", user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("вже існує");
    }

    @Test
    void addCategory_sameNameDifferentUser_isAllowed() {
        User other = new User();
        other.setUsername("other_unique_user");
        other.setPassword("encoded");
        other.setRole("USER");
        other.setPreferredCurrency("UAH");
        other = userRepository.save(other);

        categoryService.addCategory("Їжа", "EXPENSE", user);
        categoryService.addCategory("Їжа", "EXPENSE", other);

        assertThat(categoryRepository.count()).isEqualTo(2);
    }
}