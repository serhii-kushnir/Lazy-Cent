package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.BudgetService;
import org.lazycent.service.DemoDataService;
import org.lazycent.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SettingsController extends BaseController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final DemoDataService demoDataService;
    private final BudgetService budgetService;

    // ==================== ВАЛЮТА ====================

    @PostMapping("/settings/currency")
    public String updateCurrency(@RequestParam String currency,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            userService.updateCurrency(currentUser, currency);
            User updatedUser = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));
            updateSecurityContext(authentication, updatedUser);
            log.info("Валюта оновлена на {} для користувача {}", currency, updatedUser.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Валюту змінено на " + currency);
        } catch (Exception e) {
            log.error("Помилка зміни валюти: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка: " + e.getMessage());
        }
        return redirectToReferer(request);
    }

    // ==================== ПРОФІЛЬ ====================

    @PostMapping("/settings/profile")
    public String updateProfile(@RequestParam String username,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String currentPassword,
                                @RequestParam(required = false) String newPassword,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes,
                                HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            userService.updateProfile(currentUser, username, email, currentPassword, newPassword);
            User updatedUser = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));
            updateSecurityContext(authentication, updatedUser);
            redirectAttributes.addFlashAttribute("successMessage", "Профіль успішно оновлено!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectToReferer(request);
    }

    // ==================== ОЧИЩЕННЯ ДАНИХ ====================

    @PostMapping("/settings/clear-data")
    public String clearData(Authentication authentication,
                            RedirectAttributes redirectAttributes,
                            HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            budgetService.clearAllUserData(currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Всі дані успішно видалено!");
        } catch (Exception e) {
            log.error("Помилка при очищенні даних: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка при очищенні даних: " + e.getMessage());
        }
        return redirectToReferer(request);
    }

    // ==================== ВИДАЛЕННЯ АКАУНТА ====================

    @PostMapping("/settings/delete-account")
    public String deleteAccount(Authentication authentication,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            userService.deleteAccount(currentUser);
            request.getSession().invalidate();
            SecurityContextHolder.clearContext();
            redirectAttributes.addFlashAttribute("successMessage", "Акаунт успішно видалено!");
            return "redirect:/login";
        } catch (Exception e) {
            log.error("Помилка при видаленні акаунта: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка при видаленні акаунта: " + e.getMessage());
            return "redirect:/";
        }
    }

    // ==================== ДЕМО-ДАНІ ====================

    @PostMapping("/generate-demo")
    public String generateDemo(Authentication authentication,
                               RedirectAttributes redirectAttributes,
                               HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            demoDataService.createDefaultCategories(currentUser);
            demoDataService.createRandomTransactions(currentUser, 14);
            redirectAttributes.addFlashAttribute("successMessage", "Демо-дані успішно додано! 🎉");
        } catch (Exception e) {
            log.error("Помилка генерації демо-даних: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка: " + e.getMessage());
        }
        return redirectToReferer(request);
    }
}