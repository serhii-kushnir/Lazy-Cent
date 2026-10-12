package org.lazycent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lazycent.model.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    /**
     * Українська — цільова аудиторія застосунку. Fallback для тих,
     * хто не передав нічого і не має збережених налаштувань.
     */
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("uk-UA");

    /**
     * Підтримувані мови. Порядок не критичний, але тримаємо тут,
     * щоб було легко додати польську в майбутньому.
     */
    private static final List<Locale> SUPPORTED = List.of(
            DEFAULT_LOCALE,
            Locale.ENGLISH
    );

    /**
     * Резолвер локалі з наступним пріоритетом:
     * <ol>
     *   <li><b>Сесія</b> — якщо користувач явно перемкнув мову через
     *       {@code ?lang=uk} або POST {@code /settings/language}.
     *       Це той самий атрибут, який пише {@link SessionLocaleResolver},
     *       тому {@code LocaleChangeInterceptor} продовжує працювати без змін.</li>
     *   <li><b>User.preferredLanguage</b> — для автентифікованих, якщо
     *       сесія втрачена (наприклад, після рестарту застосунку або
     *       після логіну з іншого браузера). Це важливо: людина вибрала
     *       англійську — вона має бачити англійську одразу після логіну,
     *       а не тільки після першого перемикання в цій сесії.</li>
     *   <li><b>Accept-Language</b> — для нових відвідувачів. Якщо браузер
     *       явно просить англійську — показуємо англійську.</li>
     *   <li><b>Fallback</b> — {@link #DEFAULT_LOCALE} (uk-UA).</li>
     * </ol>
     */
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

    // ==================== КАСТОМНИЙ РЕЗОЛВЕР ====================

    private static class LazyCentLocaleResolver implements LocaleResolver {

        private final SessionLocaleResolver sessionDelegate = new SessionLocaleResolver();

        LazyCentLocaleResolver() {
            sessionDelegate.setDefaultLocale(DEFAULT_LOCALE);
        }

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            // 1. Сесія (пишеться LocaleChangeInterceptor або /settings/language)
            Locale sessionLocale = sessionDelegate.resolveLocale(request);
            if (isSupported(sessionLocale)) {
                return sessionLocale;
            }

            // 2. User.preferredLanguage
            Locale userLocale = resolveUserLocale();
            if (userLocale != null) {
                return userLocale;
            }

            // 3. Accept-Language
            Locale browserLocale = matchBrowserLocale(request);
            if (browserLocale != null) {
                return browserLocale;
            }

            // 4. Fallback
            return DEFAULT_LOCALE;
        }

        @Override
        public void setLocale(HttpServletRequest request,
                              HttpServletResponse response,
                              Locale locale) {
            sessionDelegate.setLocale(request, response, locale);
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

        private Locale matchBrowserLocale(HttpServletRequest request) {
            Locale browserLocale = request.getLocale();
            if (browserLocale == null) return null;
            for (Locale supported : SUPPORTED) {
                if (supported.getLanguage().equals(browserLocale.getLanguage())) {
                    return supported;
                }
            }
            return null;
        }
    }
}