package org.lazycent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.lazycent.model.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.List;
import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("uk-UA");
    private static final List<Locale> SUPPORTED = List.of(DEFAULT_LOCALE, Locale.ENGLISH);

    @Bean
    public LocaleResolver localeResolver() {
        return new LazyCentLocaleResolver();
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .resourceChain(false);
    }

    // ==================== КАСТОМНИЙ РЕЗОЛВЕР З COOKIE ====================

    public static class LazyCentLocaleResolver implements LocaleResolver {

        // Використовуємо CookieLocaleResolver для збереження в браузері на 1 рік
        private final CookieLocaleResolver cookieResolver = new CookieLocaleResolver("LAZYCENT_LANG");

        public LazyCentLocaleResolver() {
            cookieResolver.setDefaultLocale(DEFAULT_LOCALE);
            cookieResolver.setCookieMaxAge(60 * 60 * 24 * 365); // 1 рік
            cookieResolver.setCookiePath("/");
        }

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            // 1. Перевіряємо Cookie (найвищий пріоритет, бо це явний вибір користувача)
            Locale cookieLocale = cookieResolver.resolveLocale(request);
            if (isSupported(cookieLocale)) {
                return cookieLocale;
            }

            // 2. Перевіряємо Сесію
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object attr = session.getAttribute(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME);
                if (attr instanceof Locale sessionLocale && isSupported(sessionLocale)) {
                    return sessionLocale;
                }
            }

            // 3. Перевіряємо БД (якщо користувач залогінений)
            Locale userLocale = resolveUserLocale();
            if (userLocale != null) {
                return userLocale;
            }

            // 4. Fallback
            return DEFAULT_LOCALE;
        }

        @Override
        public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
            // Зберігаємо в Cookie, Сесію та оновлюємо поточний контекст
            cookieResolver.setLocale(request, response, locale);
            request.getSession().setAttribute(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME, locale);
            org.springframework.context.i18n.LocaleContextHolder.setLocale(locale);
        }

        private boolean isSupported(Locale locale) {
            if (locale == null) return false;
            return SUPPORTED.stream().anyMatch(s -> s.getLanguage().equals(locale.getLanguage()));
        }

        private Locale resolveUserLocale() {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            Object principal = auth.getPrincipal();
            User user = null;
            if (principal instanceof User u) user = u;
            else if (principal instanceof org.lazycent.model.CustomOAuth2User o) user = o.getUser();

            if (user == null || user.getPreferredLanguage() == null) return null;
            Locale locale = Locale.forLanguageTag(user.getPreferredLanguage());
            return isSupported(locale) ? locale : null;
        }
    }
}