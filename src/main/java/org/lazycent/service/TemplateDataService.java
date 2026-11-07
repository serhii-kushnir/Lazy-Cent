package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.Currency;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TemplateDataService {

    private final MessageSource messageSource;

    public Map<String, String> dayNames() {
        Locale locale = LocaleContextHolder.getLocale();
        Map<String, String> map = new HashMap<>();
        map.put("Понеділок", msg("day.monday", locale));
        map.put("Вівторок", msg("day.tuesday", locale));
        map.put("Середа", msg("day.wednesday", locale));
        map.put("Четвер", msg("day.thursday", locale));
        map.put("П'ятниця", msg("day.friday", locale));
        map.put("Субота", msg("day.saturday", locale));
        map.put("Неділя", msg("day.sunday", locale));
        return map;
    }

    public String currencySymbol() {
        User user = currentUser();
        if (user == null) return Currency.defaultCurrency().getSymbol();
        return Currency.fromCode(user.getPreferredCurrency())
                .map(Currency::getSymbol)
                .orElse(Currency.defaultCurrency().getSymbol());
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object p = auth.getPrincipal();
        if (p instanceof CustomOAuth2User o) return o.getUser();
        if (p instanceof User u) return u;
        return null;
    }

    private String msg(String key, Locale locale) {
        try {
            return messageSource.getMessage(key, null, locale);
        } catch (Exception e) {
            return key;
        }
    }
}