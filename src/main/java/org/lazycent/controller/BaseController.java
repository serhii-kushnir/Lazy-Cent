package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

@Slf4j
public abstract class BaseController {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected MessageSource messageSource;

    protected User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomOAuth2User oAuth2User) return oAuth2User.getUser();
        if (principal instanceof User user) return user;
        if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            User fromDb = userRepository.findByUsername(username).orElse(null);
            if (fromDb == null) {
                log.warn("Principal '{}' (UserDetails) не знайдено в БД — повертаємо null", username);
            }
            return fromDb;
        }
        log.warn("Невідомий тип principal: {} — getCurrentUser повертає null", principal.getClass().getName());
        return null;
    }

    protected void updateSecurityContext(Authentication authentication, User updatedUser) {
        Authentication newAuth = new UsernamePasswordAuthenticationToken(
                updatedUser,
                authentication.getCredentials(),
                updatedUser.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }

    protected String redirectToReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            return "redirect:" + referer;
        }
        return "redirect:/";
    }

    /**
     * Локалізоване повідомлення за ключем. При помилці повертає сам ключ
     * (щоб застосунок не падав у разі відсутнього перекладу).
     */
    protected String msg(String key, Object... args) {
        try {
            return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            log.warn("Не знайдено переклад для ключа '{}': {}", key, e.getMessage());
            return key;
        }
    }
}