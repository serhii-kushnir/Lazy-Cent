// config/GlobalControllerAdvice.java
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
        if (auth == null || !auth.isAuthenticated()) {
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
}