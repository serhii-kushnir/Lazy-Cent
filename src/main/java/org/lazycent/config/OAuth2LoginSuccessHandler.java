package org.lazycent.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.IOException;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        // Підтягуємо мову користувача з БД
        Object principal = authentication.getPrincipal();
        User user = null;
        if (principal instanceof CustomOAuth2User cou) {
            user = cou.getUser();
        } else if (principal instanceof User u) {
            user = u;
        }

        if (user != null && user.getPreferredLanguage() != null) {
            LocaleResolver localeResolver = RequestContextUtils.getLocaleResolver(request);
            if (localeResolver != null) {
                localeResolver.setLocale(request, response,
                        Locale.forLanguageTag(user.getPreferredLanguage()));
            }
        }

        response.sendRedirect("/");
    }
}