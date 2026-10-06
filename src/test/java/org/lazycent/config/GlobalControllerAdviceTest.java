package org.lazycent.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.Currency;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.lazycent.service.CategoryService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GlobalControllerAdviceTest {

    private CategoryService categoryService;
    private GlobalControllerAdvice advice;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk-UA"));

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);

        categoryService = mock(CategoryService.class);
        advice = new GlobalControllerAdvice(categoryService, messageSource);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        LocaleContextHolder.resetLocaleContext();
    }

    // ==================== CURRENCY ====================

    @Test
    void getCurrencySymbol_returnsUah_whenAnonymous() {
        SecurityContextHolder.clearContext();
        assertThat(advice.getCurrencySymbol()).isEqualTo("₴");
    }

    @Test
    void getCurrencySymbol_returnsSymbolOfUserCurrency() {
        User user = userWithCurrency("USD");
        authenticate(user);

        assertThat(advice.getCurrencySymbol()).isEqualTo("$");
    }

    @Test
    void getPreferredCurrency_returnsUah_whenAnonymous() {
        SecurityContextHolder.clearContext();
        assertThat(advice.getPreferredCurrency()).isEqualTo("UAH");
    }

    @Test
    void getPreferredCurrency_returnsUserCurrency() {
        User user = userWithCurrency("EUR");
        authenticate(user);

        assertThat(advice.getPreferredCurrency()).isEqualTo("EUR");
    }

    // ==================== OAUTH2 ====================

    @Test
    void isOAuth2User_returnsFalse_forRegularUser() {
        User user = userWithCurrency("UAH");
        user.setProvider(null);
        authenticate(user);

        assertThat(advice.isOAuth2User()).isFalse();
    }

    @Test
    void isOAuth2User_returnsTrue_whenProviderSet() {
        User user = userWithCurrency("UAH");
        user.setProvider("google");
        authenticate(user);

        assertThat(advice.isOAuth2User()).isTrue();
    }

    @Test
    void isOAuth2User_returnsTrue_forCustomOAuth2User() {
        User user = userWithCurrency("UAH");
        user.setProvider("google");

        OAuth2User oauth = mock(OAuth2User.class);
        CustomOAuth2User custom = new CustomOAuth2User(oauth, user);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                custom, null, custom.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(advice.isOAuth2User()).isTrue();
    }

    @Test
    void providerName_returnsGoogle_forGoogleProvider() {
        User user = userWithCurrency("UAH");
        user.setProvider("google");
        authenticate(user);

        assertThat(advice.getProviderName()).isEqualTo("Google");
    }

    @Test
    void providerName_returnsGitHub_forGithubProvider() {
        User user = userWithCurrency("UAH");
        user.setProvider("github");
        authenticate(user);

        assertThat(advice.getProviderName()).isEqualTo("GitHub");
    }

    @Test
    void providerName_returnsEmpty_forAnonymous() {
        SecurityContextHolder.clearContext();
        assertThat(advice.getProviderName()).isEmpty();
    }

    // ==================== USER EMAIL ====================

    @Test
    void getUserEmail_returnsEmail_whenSet() {
        User user = userWithCurrency("UAH");
        user.setEmail("test@example.com");
        authenticate(user);

        assertThat(advice.getUserEmail()).isEqualTo("test@example.com");
    }

    @Test
    void getUserEmail_returnsEmpty_whenAnonymous() {
        SecurityContextHolder.clearContext();
        assertThat(advice.getUserEmail()).isEmpty();
    }

    // ==================== CATEGORIES ====================

    @Test
    void populateExpenseCategories_returnsEmpty_forAnonymous() {
        SecurityContextHolder.clearContext();
        assertThat(advice.populateExpenseCategories()).isEmpty();
    }

    @Test
    void populateExpenseCategories_delegatesToService() {
        User user = userWithCurrency("UAH");
        authenticate(user);
        when(categoryService.getExpenseCategories(user)).thenReturn(List.of());

        advice.populateExpenseCategories();

        verify(categoryService).getExpenseCategories(user);
    }

    @Test
    void populateExpenseCategories_returnsEmpty_onServiceException() {
        User user = userWithCurrency("UAH");
        authenticate(user);
        when(categoryService.getExpenseCategories(user))
                .thenThrow(new RuntimeException("DB down"));

        assertThat(advice.populateExpenseCategories()).isEmpty();
    }

    // ==================== DAY NAMES ====================

    @Test
    void populateDayNames_returnsUkrainianLocalizedNames() {
        Map<String, String> names = advice.populateDayNames();

        assertThat(names)
                .containsKey("Понеділок")
                .containsKey("Неділя");
        assertThat(names.get("Понеділок")).isNotBlank();
    }

    // ==================== SUPPORTED CURRENCIES ====================

    @Test
    void getSupportedCurrencies_returnsAllCurrencies() {
        Currency[] currencies = advice.getSupportedCurrencies();
        assertThat(currencies).containsExactly(
                Currency.UAH, Currency.USD, Currency.EUR,
                Currency.GBP, Currency.PLN, Currency.CZK);
    }

    // ==================== ДОПОМІЖНІ ====================

    private User userWithCurrency(String currency) {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole("USER");
        user.setPreferredCurrency(currency);
        return user;
    }

    private void authenticate(User user) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void providerName_returnsRawValue_forUnknownProvider() {
        User user = userWithCurrency("UAH");
        user.setProvider("custom-idp");
        authenticate(user);

        assertThat(advice.getProviderName()).isEqualTo("custom-idp");
    }
}