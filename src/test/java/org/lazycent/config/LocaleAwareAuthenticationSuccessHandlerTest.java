package org.lazycent.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LocaleAwareAuthenticationSuccessHandlerTest {

    private LocaleResolver localeResolver;
    private LocaleAwareAuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        localeResolver = mock(LocaleResolver.class);
        handler = new LocaleAwareAuthenticationSuccessHandler(localeResolver);
    }

    @Test
    void onAuthenticationSuccess_redirectsToHome() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication auth = authFor(userWithLang(null));

        handler.onAuthenticationSuccess(request, response, auth);

        assertThat(response.getRedirectedUrl()).isEqualTo("/home");
    }

    @Test
    void onAuthenticationSuccess_setsLocale_whenPreferredLanguageSet() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication auth = authFor(userWithLang("uk-UA"));

        handler.onAuthenticationSuccess(request, response, auth);

        verify(localeResolver).setLocale(eq(request), eq(response), eq(Locale.forLanguageTag("uk-UA")));
    }

    @Test
    void onAuthenticationSuccess_doesNotSetLocale_whenLanguageNull() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication auth = authFor(userWithLang(null));

        handler.onAuthenticationSuccess(request, response, auth);

        verify(localeResolver, never()).setLocale(any(), any(), any());
    }

    @Test
    void onAuthenticationSuccess_handlesCustomOAuth2User() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        User user = userWithLang("en");
        OAuth2User oauth = mock(OAuth2User.class);
        CustomOAuth2User custom = new CustomOAuth2User(oauth, user);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                custom, null, custom.getAuthorities());

        handler.onAuthenticationSuccess(request, response, auth);

        assertThat(response.getRedirectedUrl()).isEqualTo("/home");
        verify(localeResolver).setLocale(eq(request), eq(response), eq(Locale.ENGLISH));
    }

    // ==================== ДОПОМІЖНІ ====================

    private User userWithLang(String lang) {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole("USER");
        user.setPreferredLanguage(lang);
        return user;
    }

    private Authentication authFor(User user) {
        return new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());
    }
}