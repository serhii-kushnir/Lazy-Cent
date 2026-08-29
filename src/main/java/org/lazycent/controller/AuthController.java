// controller/AuthController.java (виправлений)
package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.lazycent.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // ⭐ ВАЖЛИВО: створюємо сесію до рендерингу сторінки, щоб CSRF-токен був доступний
    @GetMapping("/login")
    public String login(HttpServletRequest request) {
        request.getSession(); // примусове створення сесії
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           RedirectAttributes redirect) {
        try {
            userService.registerUser(username, password);
            redirect.addFlashAttribute("successMessage", "Реєстрація успішна! Увійдіть.");
            return "redirect:/login";
        } catch (Exception e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/register";
        }
    }
}