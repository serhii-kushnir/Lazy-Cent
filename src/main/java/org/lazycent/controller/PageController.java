package org.lazycent.controller;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Locale;
import java.util.List;

@Controller
public class PageController {

    private final MessageSource messageSource;

    public PageController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

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

    @GetMapping("/family-expenses")
    public String familyExpenses(Model model) {
        return seoTopic(model, "family-expenses", "seo.familyExpenses");
    }

    @GetMapping("/budget-planning")
    public String budgetPlanning(Model model) {
        return seoTopic(model, "budget-planning", "seo.budgetPlanning");
    }

    @GetMapping("/income-tracker")
    public String incomeTracker(Model model) {
        return seoTopic(model, "income-tracker", "seo.incomeTracker");
    }

    private String seoTopic(Model model, String slug, String messagePrefix) {
        Locale locale = LocaleContextHolder.getLocale();
        boolean english = "en".equals(locale.getLanguage());
        String canonicalUrl = "https://lazycent.com/" + slug + (english ? "?lang=en" : "");

        model.addAttribute("seoTitle", message(messagePrefix + ".title", locale));
        model.addAttribute("seoDescription", message(messagePrefix + ".description", locale));
        model.addAttribute("seoHeading", message(messagePrefix + ".heading", locale));
        model.addAttribute("seoIntro", message(messagePrefix + ".intro", locale));
        model.addAttribute("seoStepsHeading", message(messagePrefix + ".stepsHeading", locale));
        model.addAttribute("seoSteps", List.of(
                message(messagePrefix + ".step1", locale),
                message(messagePrefix + ".step2", locale),
                message(messagePrefix + ".step3", locale)));
        model.addAttribute("seoSections", List.of(
                new SeoSection(message(messagePrefix + ".section1.title", locale),
                        message(messagePrefix + ".section1.body", locale)),
                new SeoSection(message(messagePrefix + ".section2.title", locale),
                        message(messagePrefix + ".section2.body", locale)),
                new SeoSection(message(messagePrefix + ".section3.title", locale),
                        message(messagePrefix + ".section3.body", locale))));
        model.addAttribute("seoFaqHeading", message(messagePrefix + ".faqHeading", locale));
        model.addAttribute("seoFaqs", List.of(
                new SeoFaq(message(messagePrefix + ".faq1.question", locale),
                        message(messagePrefix + ".faq1.answer", locale)),
                new SeoFaq(message(messagePrefix + ".faq2.question", locale),
                        message(messagePrefix + ".faq2.answer", locale)),
                new SeoFaq(message(messagePrefix + ".faq3.question", locale),
                        message(messagePrefix + ".faq3.answer", locale))));
        model.addAttribute("seoCanonicalUrl", canonicalUrl);
        model.addAttribute("seoEnglish", english);
        model.addAttribute("seoSlug", slug);
        return "seo-topic";
    }

    private String message(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }

    public record SeoSection(String title, String body) {}

    public record SeoFaq(String question, String answer) {}
}