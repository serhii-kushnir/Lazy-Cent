package org.lazycent.config;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.Category;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.lazycent.service.BudgetService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;
import java.util.List;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final BudgetService budgetService;

    // ==================== ВАЛЮТА ====================

    @ModelAttribute("currencySymbol")
    public String getCurrencySymbol() {
        String currency = getPreferredCurrency();
        return switch (currency) {
            case "USD" -> "$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "PLN" -> "zł";
            case "CZK" -> "Kč";
            default -> "₴";
        };
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
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomOAuth2User) {
            return true;
        }
        if (principal instanceof User) {
            User user = (User) principal;
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

    // ==================== КАТЕГОРІЇ (ГЛОБАЛЬНО) ====================

    /**
     * Глобально додає категорії витрат у модель для ВСІХ сторінок.
     * Це вирішує проблему, коли settingsPanel (який вимагає expenseCategories)
     * рендериться на сторінках, де контролер не додає їх у модель.
     * Якщо контролер додає своє значення — воно перекриє це (Spring дозволяє).
     */
    @ModelAttribute("expenseCategories")
    public List<Category> populateExpenseCategories() {
        User user = getCurrentUser();
        if (user == null) return Collections.emptyList();
        try {
            return budgetService.getExpenseCategories(user);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @ModelAttribute("incomeCategories")
    public List<Category> populateIncomeCategories() {
        User user = getCurrentUser();
        if (user == null) return Collections.emptyList();
        try {
            return budgetService.getIncomeCategories(user);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ==================== ДОПОМІЖНІ ====================

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomOAuth2User) {
            return ((CustomOAuth2User) principal).getUser();
        } else if (principal instanceof User) {
            return (User) principal;
        }
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
}