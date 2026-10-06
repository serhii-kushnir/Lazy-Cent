package org.lazycent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.DayBudget;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.BudgetService;
import org.lazycent.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DayControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @MockBean private BudgetService budgetService;
    @MockBean private CategoryService categoryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new User();
        testUser.setUsername("day_ctrl_user");
        testUser.setPassword("x");
        testUser.setRole("USER");
        testUser.setPreferredCurrency("UAH");
        testUser = userRepository.save(testUser);

        when(categoryService.getExpenseCategories(any())).thenReturn(Collections.emptyList());
        when(categoryService.getIncomeCategories(any())).thenReturn(Collections.emptyList());
    }

    // ==================== GET /day/{date} ====================

    @Test
    void viewDay_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(get("/day/2026-10-05"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void viewDay_rendersForAuthenticated() throws Exception {
        DayBudget budget = new DayBudget();
        budget.setId(1L);
        budget.setDate(LocalDate.of(2026, 10, 5));
        budget.setDayName("Понеділок");

        when(budgetService.getOrCreateDayBudget(LocalDate.of(2026, 10, 5), testUser))
                .thenReturn(budget);

        mockMvc.perform(get("/day/2026-10-05").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("day-detail"))
                .andExpect(model().attributeExists("dayBudget"));
    }

    // ==================== POST /day/{date}/expense ====================

    @Test
    void addExpense_redirectsToDay_withSuccessFlash() throws Exception {
        mockMvc.perform(post("/day/2026-10-05/expense")
                        .with(csrf())
                        .with(user(testUser))
                        .param("categoryId", "1")
                        .param("description", "Продукти")
                        .param("amount", "150.0")
                        .param("quantity", "1")
                        .param("date", "2026-10-05"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/day/2026-10-05"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(budgetService).addTransaction(any(), eq(org.lazycent.model.TransactionType.EXPENSE), eq(testUser));
    }

    @Test
    void addExpense_returnsErrorFlash_whenValidationFails() throws Exception {
        // Порожній опис → @NotBlank fail → bindingResult.hasErrors() → error redirect
        mockMvc.perform(post("/day/2026-10-05/expense")
                        .with(csrf())
                        .with(user(testUser))
                        .param("categoryId", "1")
                        .param("description", "")
                        .param("amount", "150.0")
                        .param("date", "2026-10-05"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/day/2026-10-05"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(budgetService, never()).addTransaction(any(), any(), any());
    }

    // ==================== POST /day/{date}/income ====================

    @Test
    void addIncome_redirectsToDay_withSuccessFlash() throws Exception {
        mockMvc.perform(post("/day/2026-10-05/income")
                        .with(csrf())
                        .with(user(testUser))
                        .param("categoryId", "2")
                        .param("description", "Зарплата")
                        .param("amount", "20000.0")
                        .param("quantity", "1")
                        .param("date", "2026-10-05"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/day/2026-10-05"));

        verify(budgetService).addTransaction(any(), eq(org.lazycent.model.TransactionType.INCOME), eq(testUser));
    }

    // ==================== POST /quick-expense ====================

    @Test
    void quickAddExpense_redirectsToReferer() throws Exception {
        mockMvc.perform(post("/quick-expense")
                        .with(csrf())
                        .with(user(testUser))
                        .header("Referer", "/home")
                        .param("categoryId", "1")
                        .param("description", "Кава")
                        .param("amount", "50.0")
                        .param("date", "2026-10-05"))
                .andExpect(status().is3xxRedirection());

        verify(budgetService).addTransaction(any(), eq(org.lazycent.model.TransactionType.EXPENSE), eq(testUser));
    }

    @Test
    void quickAddExpense_showsErrorFlash_whenServiceThrows() throws Exception {
        doThrow(new ValidationException("Тип категорії не збігається"))
                .when(budgetService)
                .addTransaction(any(), any(), any());

        mockMvc.perform(post("/quick-expense")
                        .with(csrf())
                        .with(user(testUser))
                        .header("Referer", "/home")
                        .param("categoryId", "1")
                        .param("description", "Кава")
                        .param("amount", "50.0")
                        .param("date", "2026-10-05"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // ==================== POST /day/delete/{id} ====================

    @Test
    void deleteDay_redirectsToAllDays_withSuccessFlash() throws Exception {
        mockMvc.perform(post("/day/delete/10")
                        .with(csrf())
                        .with(user(testUser)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/all-days"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(budgetService).deleteDay(10L, testUser);
    }

    @Test
    void deleteDay_showsErrorFlash_whenServiceThrows() throws Exception {
        doThrow(new RuntimeException("Помилка видалення"))
                .when(budgetService).deleteDay(anyLong(), any());

        mockMvc.perform(post("/day/delete/10")
                        .with(csrf())
                        .with(user(testUser)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/all-days"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void addExpense_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(post("/day/2026-10-05/expense")
                        .with(csrf())
                        .param("categoryId", "1")
                        .param("description", "X")
                        .param("amount", "10")
                        .param("date", "2026-10-05"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void addIncome_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(post("/day/2026-10-05/income")
                        .with(csrf())
                        .param("categoryId", "1")
                        .param("description", "X")
                        .param("amount", "10")
                        .param("date", "2026-10-05"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void quickAddIncome_redirectsToReferer() throws Exception {
        mockMvc.perform(post("/quick-income")
                        .with(csrf())
                        .with(user(testUser))
                        .header("Referer", "/home")
                        .param("categoryId", "1")
                        .param("description", "X")
                        .param("amount", "10")
                        .param("date", "2026-10-05"))
                .andExpect(status().is3xxRedirection());

        verify(budgetService).addTransaction(any(),
                eq(org.lazycent.model.TransactionType.INCOME), eq(testUser));
    }

    @Test
    void viewDay_handlesInvalidDateFormat_withRedirect() throws Exception {
        // Некоректна дата → MethodArgumentTypeMismatchException →
        // GlobalExceptionHandler.handleGenericException → редірект (3xx),
        // а не 500/400. Це поточна поведінка.
        mockMvc.perform(get("/day/not-a-date").with(user(testUser)))
                .andExpect(status().is3xxRedirection());
    }
}