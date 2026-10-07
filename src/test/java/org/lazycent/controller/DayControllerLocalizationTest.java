package org.lazycent.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.BudgetService;
import org.lazycent.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Перевіряє, що {@link DayController} використовує локалізовані повідомлення
 * (а не хардкоджені українські рядки).
 *
 * <p><b>Чому .sessionAttr, а не LocaleContextHolder:</b>
 * {@code LocaleContextHolder.setLocale(...)} у {@code @BeforeEach} не працює,
 * бо {@code DispatcherServlet.processRequest} викликає
 * {@code localeResolver.resolveLocale(request)} і перезаписує
 * {@code LocaleContextHolder}. Наш {@code localeResolver} — це
 * {@link SessionLocaleResolver}, який читає ТІЛЬКИ з сесії. Тому єдиний
 * спосіб задати локаль на час запиту — покласти її в сесію під ключем
 * {@link SessionLocaleResolver#LOCALE_SESSION_ATTRIBUTE_NAME}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DayControllerLocalizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @MockBean private BudgetService budgetService;
    @MockBean private CategoryService categoryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new User();
        testUser.setUsername("localization_test_user");
        testUser.setPassword("x");
        testUser.setRole("USER");
        testUser.setPreferredCurrency("UAH");
        testUser = userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    /**
     * Кладе локаль у сесію, щоб {@link SessionLocaleResolver} її побачив.
     * Плюс ставить {@code Accept-Language} — щоб {@code Content-Language}
     * у відповіді був коректний (для налагодження).
     */
    private MockHttpServletRequestBuilder withLocale(
            MockHttpServletRequestBuilder builder, Locale locale) {
        return builder
                .sessionAttr(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME, locale)
                .locale(locale);
    }

    // ==================== deleteDay: success ====================

    @Test
    void deleteDay_successMessage_isUkrainian_whenLocaleUk() throws Exception {
        mockMvc.perform(withLocale(post("/day/delete/10")
                        .with(csrf())
                        .with(user(testUser)),
                        Locale.forLanguageTag("uk-UA")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        containsString("видалено")));
    }

    @Test
    void deleteDay_successMessage_isEnglish_whenLocaleEn() throws Exception {
        mockMvc.perform(withLocale(post("/day/delete/10")
                        .with(csrf())
                        .with(user(testUser)),
                        Locale.ENGLISH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        containsString("deleted")))
                .andExpect(flash().attribute("successMessage",
                        not(containsString("видалено"))));
    }

    // ==================== deleteDay: error ====================

    @Test
    void deleteDay_errorMessage_isEnglish_whenLocaleEn() throws Exception {
        doThrow(new RuntimeException("boom"))
                .when(budgetService).deleteDay(anyLong(), any());

        mockMvc.perform(withLocale(post("/day/delete/10")
                        .with(csrf())
                        .with(user(testUser)),
                        Locale.ENGLISH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage",
                        containsString("Error")))
                .andExpect(flash().attribute("errorMessage",
                        not(containsString("Помилка"))));
    }

    @Test
    void deleteDay_errorMessage_isUkrainian_whenLocaleUk() throws Exception {
        doThrow(new RuntimeException("boom"))
                .when(budgetService).deleteDay(anyLong(), any());

        mockMvc.perform(withLocale(post("/day/delete/10")
                        .with(csrf())
                        .with(user(testUser)),
                        Locale.forLanguageTag("uk-UA")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage",
                        containsString("Помилка")));
    }

    // ==================== addExpense / addIncome ====================

    @Test
    void addExpense_successMessage_isEnglish_whenLocaleEn() throws Exception {
        mockMvc.perform(withLocale(post("/day/2026-10-05/expense")
                        .with(csrf())
                        .with(user(testUser))
                        .param("categoryId", "1")
                        .param("description", "Продукти")
                        .param("amount", "150.0")
                        .param("quantity", "1")
                        .param("date", "2026-10-05"),
                        Locale.ENGLISH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        not(containsString("Витрату"))));
    }

    @Test
    void addIncome_successMessage_isEnglish_whenLocaleEn() throws Exception {
        mockMvc.perform(withLocale(post("/day/2026-10-05/income")
                        .with(csrf())
                        .with(user(testUser))
                        .param("categoryId", "2")
                        .param("description", "Зарплата")
                        .param("amount", "20000.0")
                        .param("quantity", "1")
                        .param("date", "2026-10-05"),
                        Locale.ENGLISH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        not(containsString("Дохід"))));
    }

    // ==================== validation error ====================

    @Test
    void addExpense_validationError_isLocalized_whenLocaleEn() throws Exception {
        // Порожній description → @NotBlank fail
        mockMvc.perform(withLocale(post("/day/2026-10-05/expense")
                        .with(csrf())
                        .with(user(testUser))
                        .param("categoryId", "1")
                        .param("description", "")
                        .param("amount", "150.0")
                        .param("date", "2026-10-05"),
                        Locale.ENGLISH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }
}