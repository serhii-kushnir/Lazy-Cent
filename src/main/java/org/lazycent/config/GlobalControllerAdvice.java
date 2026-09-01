package org.lazycent.config;

import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return "UAH";
        }
        Object principal = auth.getPrincipal();
        User user = null;
        if (principal instanceof CustomOAuth2User) {
            user = ((CustomOAuth2User) principal).getUser();
        } else if (principal instanceof User) {
            user = (User) principal;
        }
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomOAuth2User) {
            User user = ((CustomOAuth2User) principal).getUser();
            return getProviderDisplayName(user.getProvider());
        }
        if (principal instanceof User) {
            User user = (User) principal;
            return getProviderDisplayName(user.getProvider());
        }
        return "";
    }

    // НОВИЙ МЕТОД – передаємо EMAIL у всі шаблони
    @ModelAttribute("userEmail")
    public String getUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomOAuth2User) {
            User user = ((CustomOAuth2User) principal).getUser();
            return user.getEmail() != null ? user.getEmail() : "";
        }
        if (principal instanceof User) {
            User user = (User) principal;
            return user.getEmail() != null ? user.getEmail() : "";
        }
        return "";
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