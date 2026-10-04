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
public class PasswordResetController extends BaseController {

    private final PasswordResetService passwordResetService;

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
        // FIX: локалізоване повідомлення
        redirectAttributes.addFlashAttribute("successMessage", msg("error.email.sent"));
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

        // FIX: локалізовані повідомлення
        if (!password.equals(passwordConfirm)) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("error.password.mismatch"));
            return "redirect:/reset-password?token=" + token;
        }

        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("error.password.weak"));
            return "redirect:/reset-password?token=" + token;
        }

        try {
            passwordResetService.resetPassword(token, password);
            redirectAttributes.addFlashAttribute("successMessage", msg("error.password.reset.success"));
            return "redirect:/login";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/reset-password?token=" + token;
        }
    }
}