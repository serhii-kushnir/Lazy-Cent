package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Базовий клас для всіх контролерів.
 * Містить спільні хелпери: отримання поточного користувача,
 * оновлення SecurityContext після зміни даних, редірект на Referer.
 */
public abstract class BaseController {

    /**
     * Отримує поточного користувача з Authentication.
     * Підтримує локальних (User) і OAuth2 (CustomOAuth2User) користувачів.
     * @return User або null, якщо не автентифіковано
     */
    protected User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomOAuth2User) {
            return ((CustomOAuth2User) principal).getUser();
        } else if (principal instanceof User) {
            return (User) principal;
        }
        return null;
    }

    /**
     * Оновлює SecurityContext новими даними користувача.
     * Викликається після зміни профілю / валюти, щоб зміни відобразилися без перелогіну.
     */
    protected void updateSecurityContext(Authentication authentication, User updatedUser) {
        Authentication newAuth = new UsernamePasswordAuthenticationToken(
                updatedUser,
                authentication.getCredentials(),
                authentication.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }

    /**
     * Редірект на попередню сторінку (заголовок Referer) або на "/" якщо його немає.
     */
    protected String redirectToReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            return "redirect:" + referer;
        }
        return "redirect:/";
    }
}