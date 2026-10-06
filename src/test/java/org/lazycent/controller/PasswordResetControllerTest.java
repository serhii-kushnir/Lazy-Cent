package org.lazycent.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.exception.ValidationException;
import org.lazycent.service.PasswordResetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk-UA"));
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // ==================== GET /forgot-password ====================

    @Test
    void showForgotPasswordForm_accessibleAnonymously() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"));
    }

    // ==================== POST /forgot-password ====================

    @Test
    void processForgotPassword_redirectsWithSuccess() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "user@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(passwordResetService).createPasswordResetToken("user@example.com");
    }

    // ==================== GET /reset-password ====================

    @Test
    void showResetPasswordForm_renders_withToken() throws Exception {
        mockMvc.perform(get("/reset-password").param("token", "tok-123"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(model().attribute("token", "tok-123"));
    }

    @Test
    void showResetPasswordForm_redirectsToForgot_whenTokenMissing() throws Exception {
        mockMvc.perform(get("/reset-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password"));
    }

    @Test
    void showResetPasswordForm_redirectsToForgot_whenTokenEmpty() throws Exception {
        mockMvc.perform(get("/reset-password").param("token", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password"));
    }

    // ==================== POST /reset-password ====================

    @Test
    void processResetPassword_throwsError_whenPasswordsMismatch() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "tok-123")
                        .param("password", "Password1!")
                        .param("passwordConfirm", "Different1!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reset-password?token=tok-123"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(passwordResetService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void processResetPassword_throwsError_whenPasswordWeak() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "tok-123")
                        .param("password", "weak")
                        .param("passwordConfirm", "weak"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reset-password?token=tok-123"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(passwordResetService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void processResetPassword_succeeds_withValidPassword() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "tok-123")
                        .param("password", "Password1!")
                        .param("passwordConfirm", "Password1!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(passwordResetService).resetPassword("tok-123", "Password1!");
    }

    @Test
    void processResetPassword_showsError_whenServiceThrows() throws Exception {
        doThrow(new ValidationException("Недійсний токен"))
                .when(passwordResetService).resetPassword(anyString(), anyString());

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "bad-token")
                        .param("password", "Password1!")
                        .param("passwordConfirm", "Password1!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reset-password?token=bad-token"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}