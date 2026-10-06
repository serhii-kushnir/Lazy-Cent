package org.lazycent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.Category;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Тести AJAX-ендпоінтів {@code /categories/expense} і
 * {@code /categories/income}, які повертають JSON.
 *
 * <p>Використовують {@link MockBean} для {@link CategoryService}, щоб
 * ізолювати тест від БД-логіки.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryRestControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @MockBean private CategoryService categoryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new User();
        testUser.setUsername("ajax_test_user");
        testUser.setPassword("x");
        testUser.setRole("USER");
        testUser.setPreferredCurrency("UAH");
        testUser = userRepository.save(testUser);
    }

    // ==================== /categories/expense ====================

    @Test
    void getExpenseCategories_returnsJsonArray() throws Exception {
        when(categoryService.getExpenseCategories(testUser)).thenReturn(List.of());

        mockMvc.perform(get("/categories/expense").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getExpenseCategories_returnsCategoryDtos_withoutOwner() throws Exception {
        Category cat = new Category();
        cat.setId(10L);
        cat.setName("Їжа");
        cat.setType("EXPENSE");
        cat.setOwner(testUser);

        when(categoryService.getExpenseCategories(testUser)).thenReturn(List.of(cat));

        mockMvc.perform(get("/categories/expense").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].name").value("Їжа"))
                .andExpect(jsonPath("$[0].type").value("EXPENSE"))
                // owner НЕ має бути у JSON (CategoryDto його не містить)
                .andExpect(jsonPath("$[0].owner").doesNotExist());
    }

    @Test
    void getExpenseCategories_returnsJson401_forAnonymousWithAcceptHeader() throws Exception {
        mockMvc.perform(get("/categories/expense")
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void getExpenseCategories_redirectsToLogin_forAnonymousBrowser() throws Exception {
        mockMvc.perform(get("/categories/expense")
                        .header("Accept", MediaType.TEXT_HTML_VALUE))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void getExpenseCategories_returns401Json_forXRequestedWithHeader() throws Exception {
        mockMvc.perform(get("/categories/expense")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ==================== /categories/income ====================

    @Test
    void getIncomeCategories_returnsJsonArray() throws Exception {
        when(categoryService.getIncomeCategories(testUser)).thenReturn(List.of());

        mockMvc.perform(get("/categories/income").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getIncomeCategories_returnsCategoryDtos() throws Exception {
        Category cat = new Category();
        cat.setId(20L);
        cat.setName("Зарплата");
        cat.setType("INCOME");
        cat.setOwner(testUser);

        when(categoryService.getIncomeCategories(testUser)).thenReturn(List.of(cat));

        mockMvc.perform(get("/categories/income").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(20))
                .andExpect(jsonPath("$[0].name").value("Зарплата"))
                .andExpect(jsonPath("$[0].type").value("INCOME"));
    }
}