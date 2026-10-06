package org.lazycent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.User;
import org.lazycent.model.dto.BudgetDashboardDto;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.BudgetPlanningService;
import org.lazycent.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
class BudgetControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @MockBean private BudgetPlanningService budgetPlanningService;
    @MockBean private CategoryService categoryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new User();
        testUser.setUsername("budget_ctrl_user");
        testUser.setPassword("x");
        testUser.setRole("USER");
        testUser.setPreferredCurrency("UAH");
        testUser = userRepository.save(testUser);

        BudgetDashboardDto emptyDashboard = new BudgetDashboardDto();
        emptyDashboard.setTotalBudget(0.0);
        emptyDashboard.setTotalExpenses(0.0);
        emptyDashboard.setTotalIncomes(0.0);
        emptyDashboard.setRemainingBudget(0.0);
        emptyDashboard.setCategories(Collections.emptyList());
        emptyDashboard.setCategoryExpenses(Collections.emptyMap());
        emptyDashboard.setDailyExpenses(Collections.emptyMap());
        when(budgetPlanningService.getDashboard(any(), anyInt(), anyInt()))
                .thenReturn(emptyDashboard);
        when(categoryService.getExpenseCategories(any())).thenReturn(Collections.emptyList());
    }

    // ==================== GET /budget ====================

    @Test
    void viewBudget_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(get("/budget"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void viewBudget_rendersForAuthenticated() throws Exception {
        mockMvc.perform(get("/budget").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("budget"))
                .andExpect(model().attributeExists("dashboard"))
                .andExpect(model().attributeExists("month"))
                .andExpect(model().attributeExists("year"))
                .andExpect(model().attributeExists("expenseCategories"))
                .andExpect(model().attributeExists("categoryExpenses"));
    }

    @Test
    void viewBudget_defaultsToCurrentMonthAndYear() throws Exception {
        java.time.LocalDate now = java.time.LocalDate.now();

        mockMvc.perform(get("/budget").with(user(testUser)))
                .andExpect(model().attribute("month", now.getMonthValue()))
                .andExpect(model().attribute("year", now.getYear()));
    }

    @Test
    void viewBudget_acceptsExplicitMonthAndYear() throws Exception {
        mockMvc.perform(get("/budget")
                        .param("month", "5")
                        .param("year", "2025")
                        .with(user(testUser)))
                .andExpect(model().attribute("month", 5))
                .andExpect(model().attribute("year", 2025));

        verify(budgetPlanningService).getDashboard(testUser, 5, 2025);
    }

    // ==================== POST /budget/plan ====================

    @Test
    void saveBudgetPlan_redirectsForAuthenticated() throws Exception {
        mockMvc.perform(post("/budget/plan")
                        .with(csrf())
                        .with(user(testUser))
                        .param("totalBudget", "5000.0")
                        .param("month", "10")
                        .param("year", "2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/budget?month=10&year=2026"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(budgetPlanningService).saveOrUpdateBudgetPlan(testUser, 10, 2026, 5000.0);
    }

    @Test
    void saveBudgetPlan_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(post("/budget/plan")
                        .with(csrf())
                        .param("totalBudget", "5000.0")
                        .param("month", "10")
                        .param("year", "2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ==================== POST /budget/category-limit ====================

    @Test
    void saveCategoryLimit_redirectsForAuthenticated() throws Exception {
        mockMvc.perform(post("/budget/category-limit")
                        .with(csrf())
                        .with(user(testUser))
                        .param("categoryId", "10")
                        .param("limitAmount", "500.0")
                        .param("note", "тест")
                        .param("month", "10")
                        .param("year", "2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/budget?month=10&year=2026"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(budgetPlanningService).saveCategoryLimit(
                eq(testUser), eq(10L), eq(10), eq(2026), eq(500.0), eq("тест"));
    }

    @Test
    void saveCategoryLimit_acceptsMissingNote() throws Exception {
        mockMvc.perform(post("/budget/category-limit")
                        .with(csrf())
                        .with(user(testUser))
                        .param("categoryId", "10")
                        .param("limitAmount", "500.0")
                        .param("month", "10")
                        .param("year", "2026"))
                .andExpect(status().is3xxRedirection());

        verify(budgetPlanningService).saveCategoryLimit(
                eq(testUser), eq(10L), eq(10), eq(2026), eq(500.0), isNull());
    }
}