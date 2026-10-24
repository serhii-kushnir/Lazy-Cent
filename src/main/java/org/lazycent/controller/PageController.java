package org.lazycent.controller;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Locale;

@Controller
public class PageController {

    @GetMapping("/privacy")
    public String privacy() {
        return localizedPage("privacy");
    }

    @GetMapping("/terms")
    public String terms() {
        return localizedPage("terms");
    }

    /**
     * Вибирає шаблон за локаллю:
     * - uk → privacy.html / terms.html
     * - en → privacy_en.html / terms_en.html
     */
    private String localizedPage(String baseName) {
        Locale locale = LocaleContextHolder.getLocale();
        if ("en".equals(locale.getLanguage())) {
            return baseName + "_en";
        }
        return baseName;
    }

    @GetMapping("/about")
    public String about() {
        return "about"; // створіть шаблон about.html за зразком privacy.html
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }

    @GetMapping("/editorial-policy")
    public String editorialPolicy() {
        return "editorial-policy";
    }
}