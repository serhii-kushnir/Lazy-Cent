package org.lazycent.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebConfigTest {

    private final WebConfig config = new WebConfig();

    // ==================== LOCALE RESOLVER ====================

    @Test
    void localeResolver_isNotNull() {
        LocaleResolver resolver = config.localeResolver();
        assertThat(resolver).isNotNull();
    }

    @Test
    void localeResolver_defaultsToUkrainian_whenNoSessionAndNoAcceptLanguage() {
        LocaleResolver resolver = config.localeResolver();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Новий LazyCentLocaleResolver: якщо немає сесії, немає User, немає Accept-Language
        // → fallback на uk-UA.
        assertThat(resolver.resolveLocale(request))
                .isEqualTo(Locale.forLanguageTag("uk-UA"));
    }

    @Test
    void localeResolver_usesAcceptLanguage_whenSupported() {
        LocaleResolver resolver = config.localeResolver();

        MockHttpServletRequest request = new MockHttpServletRequest();
        // Симулюємо заголовок Accept-Language: en-US,en;q=0.9
        request.addHeader("Accept-Language", "en-US,en;q=0.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(resolver.resolveLocale(request))
                .isEqualTo(Locale.ENGLISH);
    }

    @Test
    void localeResolver_usesSessionLocale_whenSet() {
        LocaleResolver resolver = config.localeResolver();

        MockHttpServletRequest request = new MockHttpServletRequest();
        // LazyCentLocaleResolver делегує перевірку сесії SessionLocaleResolver.
        // Кладемо локаль у сесію під тим самим ключем.
        request.getSession().setAttribute(
                SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME,
                Locale.ENGLISH
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(resolver.resolveLocale(request))
                .isEqualTo(Locale.ENGLISH);
    }

    // ==================== LOCALE CHANGE INTERCEPTOR ====================

    @Test
    void localeChangeInterceptor_paramNameIsLang() {
        LocaleChangeInterceptor interceptor = config.localeChangeInterceptor();
        assertThat(interceptor.getParamName()).isEqualTo("lang");
    }

    // ==================== INTERCEPTOR REGISTRATION ====================

    @Test
    void addInterceptors_registersLocaleChangeInterceptor() {
        InterceptorRegistry registry = mock(InterceptorRegistry.class);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(any(LocaleChangeInterceptor.class));
    }

    // ==================== RESOURCE HANDLER ====================

    /**
     * Перевіряє, що метод {@code addResourceHandlers} виконується без
     * помилок і реєструє шлях {@code /webjars/**}.
     *
     * <p><b>Чому RETURNS_DEEP_STUBS:</b> методи
     * {@code ResourceHandlerRegistration.addResourceLocations(String...)}
     * — це varargs-методи, які повертають той самий
     * {@code ResourceHandlerRegistration} для chaining. Мокати їх
     * вручну через {@code when().thenReturn()} — боляче через
     * inference varargs. Deep stubs вирішують це автоматично.
     */
    @Test
    void addResourceHandlers_registersWebjarsPath() {
        ResourceHandlerRegistry registry = mock(
                ResourceHandlerRegistry.class, Mockito.RETURNS_DEEP_STUBS);

        config.addResourceHandlers(registry);

        verify(registry).addResourceHandler("/webjars/**");
    }

    @Test
    void addResourceHandlers_doesNotThrow() {
        ResourceHandlerRegistry registry = mock(
                ResourceHandlerRegistry.class, Mockito.RETURNS_DEEP_STUBS);

        assertThatCode(() -> config.addResourceHandlers(registry))
                .doesNotThrowAnyException();
    }
}