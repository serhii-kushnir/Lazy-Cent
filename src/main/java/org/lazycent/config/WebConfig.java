package org.lazycent.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.lazycent.model.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
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

    public static class LazyCentLocaleResolver implements LocaleResolver {
        private static final String COOKIE_NAME = "LAZYCENT_LANG";

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            // 1. Cookie (найвищий пріоритет, бо це явний вибір)
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (COOKIE_NAME.equals(cookie.getName())) {
                        Locale cookieLocale = Locale.forLanguageTag(cookie.getValue());
                        if (isSupported(cookieLocale)) {
                            return cookieLocale;
                        }
                    }
                }
            }

            // 2. Сесія (використовується в тестах та при першому виборі мови)
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object attr = session.getAttribute(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME);
                if (attr instanceof Locale sessionLocale && isSupported(sessionLocale)) {
                    return sessionLocale;
                }
            }

            // 3. БД (якщо користувач залогінений і вибрав мову в профілі)
            Locale userLocale = resolveUserLocale();
            if (userLocale != null) {
                return userLocale;
            }

            // 4. Fallback
            return DEFAULT_LOCALE;
        }

        @Override
        public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
            // Зберігаємо в Cookie (терміном на 1 рік)
            Cookie cookie = new Cookie(COOKIE_NAME, locale.toLanguageTag());
            cookie.setMaxAge(60 * 60 * 24 * 365);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            response.addCookie(cookie);

            // Зберігаємо в Сесію
            request.getSession().setAttribute(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME, locale);

            // Оновлюємо поточний контекст
            LocaleContextHolder.setLocale(locale);
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