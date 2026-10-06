package org.lazycent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.BudgetService;
import org.lazycent.service.DemoDataService;
import org.lazycent.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
class SettingsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @MockBean private UserService userService;
    @MockBean private DemoDataService demoDataService;
    @MockBean private BudgetService budgetService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new User();
        testUser.setUsername("settings_ctrl_user");
        testUser.setPassword("x");
        testUser.setRole("USER");
        testUser.setPreferredCurrency("UAH");
        testUser = userRepository.save(testUser);
    }

    // ==================== /settings/currency ====================

    @Test
    void updateCurrency_redirectsWithSuccess_forValidCurrency() throws Exception {
        mockMvc.perform(post("/settings/currency")
                        .with(csrf())
                        .with(user(testUser))
                        .header("Referer", "/home")
                        .param("currency", "USD"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        verify(userService).updateCurrency(testUser, "USD");
    }

    @Test
    void updateCurrency_showsErrorFlash_whenInvalidCurrency() throws Exception {
        doThrow(new ValidationException("Непідтримувана валюта"))
                .when(userService).updateCurrency(any(), eq("XYZ"));

        mockMvc.perform(post("/settings/currency")
                        .with(csrf())
                        .with(user(testUser))
                        .param("currency", "XYZ"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // ==================== /settings/profile ====================

    @Test
    void updateProfile_redirectsWithSuccess() throws Exception {
        mockMvc.perform(post("/settings/profile")
                        .with(csrf())
                        .with(user(testUser))
                        .header("Referer", "/home")
                        .param("username", "newname")
                        .param("email", "new@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        verify(userService).updateProfile(eq(testUser), eq("newname"), eq("new@example.com"), any(), any());
    }

    @Test
    void updateProfile_showsErrorFlash_whenValidationFails() throws Exception {
        doThrow(new ValidationException("Користувач вже існує"))
                .when(userService).updateProfile(any(), any(), any(), any(), any());

        mockMvc.perform(post("/settings/profile")
                        .with(csrf())
                        .with(user(testUser))
                        .param("username", "taken"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // ==================== /settings/language ====================

    @Test
    void updateLanguage_toUkrainian_redirectsWithSuccess() throws Exception {
        mockMvc.perform(post("/settings/language")
                        .with(csrf())
                        .with(user(testUser))
                        .header("Referer", "/home")
                        .param("lang", "uk"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void updateLanguage_toEnglish_redirectsWithSuccess() throws Exception {
        mockMvc.perform(post("/settings/language")
                        .with(csrf())
                        .with(user(testUser))
                        .header("Referer", "/home")
                        .param("lang", "en"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void updateLanguage_defaultsToEnglish_forUnknownLanguage() throws Exception {
        mockMvc.perform(post("/settings/language")
                        .with(csrf())
                        .with(user(testUser))
                        .param("lang", "xx"))
                .andExpect(status().is3xxRedirection());
    }

    // ==================== /settings/clear-data ====================

    @Test
    void clearData_redirectsWithSuccess() throws Exception {
        mockMvc.perform(post("/settings/clear-data")
                        .with(csrf())
                        .with(user(testUser)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        verify(budgetService).clearAllUserData(testUser);
    }

    // ==================== /settings/delete-account ====================

    @Test
    void deleteAccount_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/settings/delete-account")
                        .with(csrf())
                        .with(user(testUser)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(userService).deleteAccount(testUser);
    }

    // ==================== /generate-demo ====================

    @Test
    void generateDemo_redirectsWithSuccess() throws Exception {
        mockMvc.perform(post("/generate-demo")
                        .with(csrf())
                        .with(user(testUser)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        verify(demoDataService).createDefaultCategories(testUser);
        verify(demoDataService).createRandomTransactions(eq(testUser), anyInt());
    }

    @Test
    void generateDemo_showsErrorFlash_whenServiceThrows() throws Exception {
        doThrow(new RuntimeException("Помилка генерації"))
                .when(demoDataService).createRandomTransactions(any(), anyInt());

        mockMvc.perform(post("/generate-demo")
                        .with(csrf())
                        .with(user(testUser)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // ==================== Анонімний доступ ====================

    @Test
    void updateCurrency_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(post("/settings/currency")
                        .with(csrf())
                        .param("currency", "USD"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}