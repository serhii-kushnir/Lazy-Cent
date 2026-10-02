package org.lazycent.config;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.Category;
import org.lazycent.model.Currency;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.lazycent.service.CategoryService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final CategoryService categoryService;
    private final MessageSource messageSource;

    // ==================== ВАЛЮТА ====================

    @ModelAttribute("currencySymbol")
    public String getCurrencySymbol() {
        String code = getPreferredCurrency();
        return Currency.fromCode(code)
                .map(Currency::getSymbol)
                .orElse(Currency.defaultCurrency().getSymbol());
    }

    @ModelAttribute("preferredCurrency")
    public String getPreferredCurrency() {
        User user = getCurrentUser();
        if (user != null && user.getPreferredCurrency() != null) {
            return user.getPreferredCurrency();
        }
        return "UAH";
    }

    @ModelAttribute("isOAuth2User")
    public boolean isOAuth2User() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomOAuth2User) return true;
        if (principal instanceof User user) {
            return user.getProvider() != null && !user.getProvider().isEmpty();
        }
        return false;
    }

    @ModelAttribute("providerName")
    public String getProviderName() {
        User user = getCurrentUser();
        if (user == null) return "";
        return getProviderDisplayName(user.getProvider());
    }

    @ModelAttribute("userEmail")
    public String getUserEmail() {
        User user = getCurrentUser();
        if (user == null) return "";
        return user.getEmail() != null ? user.getEmail() : "";
    }

    // ==================== КАТЕГОРІЇ ====================

    @ModelAttribute("expenseCategories")
    public List<Category> populateExpenseCategories() {
        User user = getCurrentUser();
        if (user == null) return Collections.emptyList();
        try {
            return categoryService.getExpenseCategories(user);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @ModelAttribute("incomeCategories")
    public List<Category> populateIncomeCategories() {
        User user = getCurrentUser();
        if (user == null) return Collections.emptyList();
        try {
            return categoryService.getIncomeCategories(user);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ==================== ДНІ ТИЖНЯ (ЛОКАЛІЗОВАНІ) ====================

    /**
     * Мапа: українська назва дня з БД → локалізована назва для поточного locale.
     * Використовується в шаблонах:
     *   th:text="${dayNames[budget.dayName]} ?: ${budget.dayName}"
     */
    @ModelAttribute("dayNames")
    public Map<String, String> populateDayNames() {
        Map<String, String> map = new HashMap<>();
        map.put("Понеділок", msg("day.monday"));
        map.put("Вівторок",  msg("day.tuesday"));
        map.put("Середа",    msg("day.wednesday"));
        map.put("Четвер",    msg("day.thursday"));
        map.put("П'ятниця",  msg("day.friday"));
        map.put("Субота",    msg("day.saturday"));
        map.put("Неділя",    msg("day.sunday"));
        return map;
    }

    // ==================== ДОПОМІЖНІ ====================

    private String msg(String key) {
        try {
            return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return key;
        }
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomOAuth2User) return ((CustomOAuth2User) principal).getUser();
        if (principal instanceof User) return (User) principal;
        return null;
    }

    private String getProviderDisplayName(String provider) {
        if (provider == null) return "";
        return switch (provider.toLowerCase()) {
            case "google" -> "Google";
            case "github" -> "GitHub";
            default -> provider;
        };
    }

    @ModelAttribute("supportedCurrencies")
    public Currency[] getSupportedCurrencies() {
        return Currency.values();
    }
}