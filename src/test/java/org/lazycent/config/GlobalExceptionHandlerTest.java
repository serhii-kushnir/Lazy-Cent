package org.lazycent.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.exception.ValidationException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private RedirectUtils redirectUtils;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk-UA"));

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);

        redirectUtils = mock(RedirectUtils.class);
        when(redirectUtils.redirectToReferer(any()))
                .thenReturn("redirect:/");

        handler = new GlobalExceptionHandler(messageSource, redirectUtils);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // ==================== ДОПОМІЖНІ ====================

    /**
     * {@link RedirectAttributes#getFlashAttributes()} повертає
     * {@code Map<String, ?>}, тому AssertJ не може вивести тип для
     * {@code containsEntry(String, String)}. Приводимо до
     * {@code Map<String, Object>}.
     */
    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> flashOf(RedirectAttributes attrs) {
        return (java.util.Map<String, Object>) attrs.getFlashAttributes();
    }

    // ==================== ТЕСТИ ====================

    @Test
    void handleAccessDenied_addsErrorFlash_andRedirects() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        RedirectAttributes attrs = new RedirectAttributesModelMap();

        String result = handler.handleAccessDenied(
                new AccessDeniedException("denied"), request, attrs);

        assertThat(result).isEqualTo("redirect:/");
        assertThat(flashOf(attrs)).containsKey("errorMessage");
    }

    @Test
    void handleBusinessException_usesUserMessage() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        RedirectAttributes attrs = new RedirectAttributesModelMap();

        handler.handleBusinessException(
                new ValidationException("custom user message"), request, attrs);

        assertThat(flashOf(attrs))
                .containsEntry("errorMessage", "custom user message");
    }

    @Test
    void handleGenericException_addsLocalizedError() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        RedirectAttributes attrs = new RedirectAttributesModelMap();

        handler.handleGenericException(
                new RuntimeException("boom"), request, attrs);

        Object errorMessage = flashOf(attrs).get("errorMessage");
        assertThat(errorMessage)
                .as("Повідомлення має бути локалізоване через error.technical")
                .isNotNull()
                .asString()
                .doesNotContain("error.technical");
    }

    @Test
    void handleAccessDenied_usesExceptionMessage_whenPresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        RedirectAttributes attrs = new RedirectAttributesModelMap();

        handler.handleAccessDenied(
                new AccessDeniedException("У вас немає прав"), request, attrs);

        assertThat(flashOf(attrs))
                .containsEntry("errorMessage", "У вас немає прав");
    }

    @Test
    void handleAccessDenied_usesLocalizedMessage_whenExceptionMessageNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        RedirectAttributes attrs = new RedirectAttributesModelMap();

        handler.handleAccessDenied(
                new AccessDeniedException(null), request, attrs);

        Object errorMessage = flashOf(attrs).get("errorMessage");
        assertThat(errorMessage)
                .isNotNull()
                .asString()
                .doesNotContain("error.access.denied");
    }
}