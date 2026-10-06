package org.lazycent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.BudgetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @MockBean private BudgetService budgetService;

    private User testUser;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new User();
        testUser.setUsername("tx_ctrl_user");
        testUser.setPassword("x");
        testUser.setRole("USER");
        testUser.setPreferredCurrency("UAH");
        testUser = userRepository.save(testUser);

        testDate = LocalDate.of(2026, 10, 5);
    }

    // ==================== DUPLICATE ====================

    @Test
    void duplicateExpense_redirectsToDay() throws Exception {
        when(budgetService.duplicateExpense(10L, testUser)).thenReturn(testDate);

        mockMvc.perform(post("/expense/duplicate/10")
                        .with(csrf())
                        .with(user(testUser)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/day/" + testDate))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void duplicateIncome_redirectsToDay() throws Exception {
        when(budgetService.duplicateIncome(20L, testUser)).thenReturn(testDate);

        mockMvc.perform(post("/income/duplicate/20")
                        .with(csrf())
                        .with(user(testUser)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/day/" + testDate))
                .andExpect(flash().attributeExists("successMessage"));
    }

    // ==================== DELETE ====================

    @Test
    void deleteExpense_redirectsToDay() throws Exception {
        when(budgetService.getDateByExpense(10L, testUser)).thenReturn(testDate);

        mockMvc.perform(post("/expense/delete/10")
                        .with(csrf())
                        .with(user(testUser)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/day/" + testDate))
                .andExpect(flash().attributeExists("successMessage"));

        verify(budgetService).deleteExpense(10L, testUser);
    }

    @Test
    void deleteIncome_redirectsToDay() throws Exception {
        when(budgetService.getDateByIncome(20L, testUser)).thenReturn(testDate);

        mockMvc.perform(post("/income/delete/20")
                        .with(csrf())
                        .with(user(testUser)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/day/" + testDate));

        verify(budgetService).deleteIncome(20L, testUser);
    }

    // ==================== EDIT ====================

    @Test
    void editExpense_redirectsToDay() throws Exception {
        when(budgetService.getDateByExpense(10L, testUser)).thenReturn(testDate);

        mockMvc.perform(post("/expense/edit/10")
                        .with(csrf())
                        .with(user(testUser))
                        .param("categoryId", "1")
                        .param("description", "Оновлено")
                        .param("amount", "150.0")
                        .param("quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/day/" + testDate))
                .andExpect(flash().attributeExists("successMessage"));

        verify(budgetService).updateExpense(
                eq(10L), eq("Оновлено"), eq(150.0), eq(1L),
                eq(1), isNull(), eq(false), isNull(), isNull(), isNull(), eq(testUser));
    }

    @Test
    void editExpense_parsesTimeString() throws Exception {
        when(budgetService.getDateByExpense(10L, testUser)).thenReturn(testDate);

        mockMvc.perform(post("/expense/edit/10")
                        .with(csrf())
                        .with(user(testUser))
                        .param("categoryId", "1")
                        .param("description", "X")
                        .param("amount", "10.0")
                        .param("time", "14:30"))
                .andExpect(status().is3xxRedirection());

        verify(budgetService).updateExpense(
                eq(10L), eq("X"), eq(10.0), eq(1L),
                eq(1), isNull(), eq(false), eq(java.time.LocalTime.of(14, 30)),
                isNull(), isNull(), eq(testUser));
    }

    @Test
    void editIncome_redirectsToDay() throws Exception {
        when(budgetService.getDateByIncome(20L, testUser)).thenReturn(testDate);

        mockMvc.perform(post("/income/edit/20")
                        .with(csrf())
                        .with(user(testUser))
                        .param("categoryId", "2")
                        .param("description", "Зарплата")
                        .param("amount", "20000.0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/day/" + testDate));

        verify(budgetService).updateIncome(
                eq(20L), eq("Зарплата"), eq(20000.0), eq(2L),
                eq(1), isNull(), eq(false), isNull(), isNull(), isNull(), eq(testUser));
    }

    // ==================== ANONYMOUS ====================

    @Test
    void duplicateExpense_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(post("/expense/duplicate/10").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void editExpense_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(post("/expense/edit/10")
                        .with(csrf())
                        .param("categoryId", "1")
                        .param("description", "X")
                        .param("amount", "10.0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}