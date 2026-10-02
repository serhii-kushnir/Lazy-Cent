package org.lazycent.controller;

import org.lazycent.model.dto.RegistrationForm;
import org.lazycent.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(HttpServletRequest request) {
        request.getSession();
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model, HttpServletRequest request) {
        request.getSession();
        // Обов'язково: треба покласти порожній об'єкт у модель, бо шаблон використовує th:object="${registrationForm}"
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirect) {

        Map<String, String> fieldErrors = new HashMap<>();

        // 1. Помилки Bean Validation
        if (bindingResult.hasErrors()) {
            bindingResult.getFieldErrors().forEach(fe ->
                    fieldErrors.put(fe.getField(), fe.getDefaultMessage())
            );
        }

        // 2. Кастомна перевірка: збіг паролів
        if (form.getPassword() != null && form.getPasswordConfirm() != null
                && !form.getPassword().equals(form.getPasswordConfirm())) {
            fieldErrors.put("passwordConfirm", "Паролі не збігаються.");
        }

        // 3. Кастомна перевірка: унікальність username
        if (form.getUsername() != null && !fieldErrors.containsKey("username")
                && userService.findByUsername(form.getUsername()) != null) {
            fieldErrors.put("username", "Користувач з таким ім'ям вже існує.");
        }

        // 4. Кастомна перевірка: унікальність email
        if (form.getEmail() != null && !fieldErrors.containsKey("email")
                && userService.findByEmail(form.getEmail()) != null) {
            fieldErrors.put("email", "Користувач з такою електронною поштою вже зареєстрований.");
        }

        // Якщо є помилки — повертаємо на сторінку реєстрації
        if (!fieldErrors.isEmpty()) {
            // Не показуємо пароль повторно — з міркувань безпеки
            form.setPassword(null);
            form.setPasswordConfirm(null);

            model.addAttribute("fieldErrors", fieldErrors);
            return "register";
        }

        // Реєстрація
        try {
            userService.registerUser(form.getUsername(), form.getPassword(), form.getEmail());
            redirect.addFlashAttribute("successMessage", "Реєстрація успішна! Тепер ви можете увійти.");
            return "redirect:/login";
        } catch (Exception e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/register";
        }
    }
}