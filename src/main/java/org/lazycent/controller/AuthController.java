package org.lazycent.controller;

import jakarta.validation.Valid;
import org.lazycent.model.dto.RegistrationForm;
import org.lazycent.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthController extends BaseController {

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

        if (bindingResult.hasErrors()) {
            bindingResult.getFieldErrors().forEach(fe ->
                    fieldErrors.put(fe.getField(), fe.getDefaultMessage())
            );
        }

        // FIX: локалізовані повідомлення через msg()
        if (form.getPassword() != null && form.getPasswordConfirm() != null
                && !form.getPassword().equals(form.getPasswordConfirm())) {
            fieldErrors.put("passwordConfirm", msg("error.password.mismatch"));
        }

        if (form.getUsername() != null && !fieldErrors.containsKey("username")
                && userService.findByUsername(form.getUsername()) != null) {
            fieldErrors.put("username", msg("error.username.exists"));
        }

        if (form.getEmail() != null && !fieldErrors.containsKey("email")
                && userService.findByEmail(form.getEmail()) != null) {
            fieldErrors.put("email", msg("error.email.exists"));
        }

        if (!fieldErrors.isEmpty()) {
            form.setPassword(null);
            form.setPasswordConfirm(null);
            model.addAttribute("fieldErrors", fieldErrors);
            return "register";
        }

        try {
            userService.registerUser(form.getUsername(), form.getPassword(), form.getEmail());
            redirect.addFlashAttribute("successMessage", msg("error.registration.success"));
            return "redirect:/login";
        } catch (Exception e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/register";
        }
    }
}