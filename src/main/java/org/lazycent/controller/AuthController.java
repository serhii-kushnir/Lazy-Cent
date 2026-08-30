package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.lazycent.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Controller
public class AuthController {

    private final UserService userService;

    // Патерн для валідації email
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$");
    // Патерн для username (літери, цифри, підкреслення, мінімум 6)
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{6,30}$");

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(HttpServletRequest request) {
        request.getSession();
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(HttpServletRequest request) {
        request.getSession();
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam String passwordConfirm,
                           @RequestParam(value = "privacyConsent", defaultValue = "false") boolean privacyConsent,
                           @RequestParam(value = "termsConsent", defaultValue = "false") boolean termsConsent,
                           Model model,
                           RedirectAttributes redirect) {

        // === Валідація ===
        Map<String, String> fieldErrors = new HashMap<>();

        // 1. Перевірка username
        if (username == null || username.trim().isEmpty()) {
            fieldErrors.put("username", "Ім'я користувача не може бути порожнім.");
        } else if (!USERNAME_PATTERN.matcher(username).matches()) {
            fieldErrors.put("username", "Ім'я користувача має містити 6-30 символів (літери, цифри, підкреслення).");
        } else if (userService.findByUsername(username) != null) {
            fieldErrors.put("username", "Користувач з таким ім'ям вже існує.");
        }

        // 2. Перевірка email
        if (email == null || email.trim().isEmpty()) {
            fieldErrors.put("email", "Електронна пошта не може бути порожньою.");
        } else if (!EMAIL_PATTERN.matcher(email).matches()) {
            fieldErrors.put("email", "Введіть коректну електронну пошту.");
        } else if (userService.findByEmail(email) != null) {
            fieldErrors.put("email", "Користувач з такою електронною поштою вже зареєстрований.");
        }

        // 3. Перевірка пароля
        if (password == null || password.length() < 6) {
            fieldErrors.put("password", "Пароль має містити щонайменше 6 символів.");
        }

        // 4. Перевірка збігу паролів
        if (password != null && passwordConfirm != null && !password.equals(passwordConfirm)) {
            fieldErrors.put("passwordConfirm", "Паролі не збігаються.");
        }

        // 5. Перевірка чекбоксів
        if (!privacyConsent) {
            fieldErrors.put("privacyConsent", "Необхідно погодитися з Політикою конфіденційності.");
        }
        if (!termsConsent) {
            fieldErrors.put("termsConsent", "Необхідно погодитися з Умовами використання.");
        }

        // Якщо є помилки – повертаємо на сторінку реєстрації
        if (!fieldErrors.isEmpty()) {
            model.addAttribute("fieldErrors", fieldErrors);
            // Передаємо введені значення для зручності
            model.addAttribute("username", username);
            model.addAttribute("email", email);
            return "register";
        }

        // Реєстрація
        try {
            userService.registerUser(username, password, email);
            redirect.addFlashAttribute("successMessage", "Реєстрація успішна! Тепер ви можете увійти.");
            return "redirect:/login";
        } catch (Exception e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/register";
        }
    }
}