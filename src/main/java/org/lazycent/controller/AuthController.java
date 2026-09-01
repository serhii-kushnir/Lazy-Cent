package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.lazycent.model.dto.RegistrationForm;
import org.lazycent.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
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
        model.addAttribute("registrationForm", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirect) {

        Map<String, String> fieldErrors = new HashMap<>();

        // Додаткова перевірка унікальності
        if (userService.findByUsername(form.getUsername()) != null) {
            bindingResult.rejectValue("username", "error.user", "Користувач з таким ім'ям вже існує");
        }
        if (userService.findByEmail(form.getEmail()) != null) {
            bindingResult.rejectValue("email", "error.user", "Користувач з такою електронною поштою вже зареєстрований");
        }

        // Перевірка збігу паролів
        if (!form.getPassword().equals(form.getPasswordConfirm())) {
            bindingResult.rejectValue("passwordConfirm", "error.user", "Паролі не збігаються");
        }

        if (bindingResult.hasErrors()) {
            for (FieldError error : bindingResult.getFieldErrors()) {
                fieldErrors.put(error.getField(), error.getDefaultMessage());
            }
            model.addAttribute("fieldErrors", fieldErrors);
            model.addAttribute("username", form.getUsername());
            model.addAttribute("email", form.getEmail());
            return "register";
        }

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