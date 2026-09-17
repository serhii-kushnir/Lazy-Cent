package org.lazycent.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocaleAwareAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final LocaleResolver localeResolver;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        User user = extractUser(authentication);
        String lang = (user != null) ? user.getPreferredLanguage() : null;

        if (lang != null && !lang.isBlank()) {
            localeResolver.setLocale(request, response, Locale.forLanguageTag(lang));
            log.debug("Locale restored for {}: {}", authentication.getName(), lang);
        } else {
            log.debug("No preferredLanguage for {}, keeping default", authentication.getName());
        }

        // ← Редірект на дашборд
        response.sendRedirect(request.getContextPath() + "/home");
    }

    private User extractUser(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomOAuth2User cou) return cou.getUser();
        if (principal instanceof User u) return u;
        return null;
    }
}