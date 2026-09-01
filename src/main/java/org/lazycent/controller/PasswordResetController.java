package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import org.lazycent.service.PasswordResetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.regex.Pattern;

@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    // Регулярний вираз для перевірки пароля (той самий, що й у реєстрації)
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{6,}$");

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email,
                                        RedirectAttributes redirectAttributes) {
        passwordResetService.createPasswordResetToken(email);
        redirectAttributes.addFlashAttribute("successMessage",
                "Якщо акаунт з таким email існує, на нього було надіслано лист з інструкціями.");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam(required = false) String token,
                                        Model model) {
        if (token == null || token.isEmpty()) {
            return "redirect:/forgot-password";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam String token,
                                       @RequestParam String password,
                                       @RequestParam String passwordConfirm,
                                       RedirectAttributes redirectAttributes) {

        // 1. Перевірка збігу паролів
        if (!password.equals(passwordConfirm)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Паролі не збігаються.");
            return "redirect:/reset-password?token=" + token;
        }

        // 2. Валідація пароля (мінімальні вимоги)
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Пароль має містити мінімум 6 символів, одну малу літеру, одну велику літеру, одну цифру та один спецсимвол (@$!%*?&).");
            return "redirect:/reset-password?token=" + token;
        }

        // 3. Спроба скидання
        try {
            passwordResetService.resetPassword(token, password);
            redirectAttributes.addFlashAttribute("successMessage", "Пароль успішно змінено! Увійдіть.");
            return "redirect:/login";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/reset-password?token=" + token;
        }
    }
}