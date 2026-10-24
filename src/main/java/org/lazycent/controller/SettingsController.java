package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SettingsController extends BaseController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final DemoDataService demoDataService;
    private final BudgetService budgetService;
    private final LocaleResolver localeResolver;

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
            redirectAttributes.addFlashAttribute("successMessage", msg("toast.currency.changed", currency));
        } catch (Exception e) {
            log.error("Помилка зміни валюти: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectUtils.redirectToReferer(request);
    }

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
            redirectAttributes.addFlashAttribute("successMessage", msg("toast.profile.updated"));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectUtils.redirectToReferer(request);
    }

    @PostMapping("/settings/clear-data")
    public String clearData(Authentication authentication,
                            RedirectAttributes redirectAttributes,
                            HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            budgetService.clearAllUserData(currentUser);
            redirectAttributes.addFlashAttribute("successMessage", msg("toast.data.cleared"));
        } catch (Exception e) {
            log.error("Помилка при очищенні даних: ", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    msg("toast.data.clearError", e.getMessage()));
        }
        return redirectUtils.redirectToReferer(request);
    }

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
            redirectAttributes.addFlashAttribute("successMessage", msg("toast.account.deleted"));
            return "redirect:/login";
        } catch (Exception e) {
            log.error("Помилка при видаленні акаунта: ", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    msg("toast.account.deleteError", e.getMessage()));
            return "redirect:/";
        }
    }

    @PostMapping("/generate-demo")
    public String generateDemo(Authentication authentication,
                               RedirectAttributes redirectAttributes,
                               HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            demoDataService.createDefaultCategories(currentUser);
            demoDataService.createRandomTransactions(currentUser, 14);
            redirectAttributes.addFlashAttribute("successMessage", msg("toast.demo.added"));
        } catch (Exception e) {
            log.error("Помилка генерації демо-даних: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectUtils.redirectToReferer(request);
    }

    @PostMapping("/settings/language")
    public String updateLanguage(@RequestParam String lang,
                                 Authentication authentication,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) {
        Locale locale;
        switch (lang) {
            case "en" -> locale = Locale.ENGLISH;
            case "uk" -> locale = Locale.forLanguageTag("uk-UA");
            default -> locale = Locale.forLanguageTag("uk-UA");
        }

        // Зберігаємо локаль через наш LocaleResolver (Cookie + Session)
        localeResolver.setLocale(request, response, locale);

        // Оновлюємо БД для залогіненого користувача
        User currentUser = getCurrentUser(authentication);
        if (currentUser != null) {
            try {
                userRepository.updatePreferredLanguage(currentUser.getId(), locale.getLanguage());
                log.info("Мову користувача {} змінено на {}", currentUser.getUsername(), locale.getLanguage());
            } catch (Exception e) {
                log.warn("Не вдалось зберегти preferredLanguage для {}: {}",
                        currentUser.getUsername(), e.getMessage());
            }
        }

        String message = msg("toast.language.changed." + lang);
        redirectAttributes.addFlashAttribute("successMessage", message);

        return redirectUtils.redirectToReferer(request);
    }
}