package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Базовий клас для всіх контролерів.
 * <p>
 * Містить спільні хелпери:
 * <ul>
 *   <li>отримання поточного користувача (з підтримкою OAuth2, UserDetails, User)</li>
 *   <li>оновлення SecurityContext після зміни профілю</li>
 *   <li>редірект на Referer</li>
 * </ul>
 *
 * <p><b>Чому тут field injection, а не constructor:</b> це абстрактний батько для
 * {@code @Controller}-класів. Lombok {@code @RequiredArgsConstructor} генерує
 * конструктор тільки з полів СВОГО класу й не викликає {@code super(...)}, тому
 * конструкторна ін'єкція в базовому класі ламає автовайринг 10 нащадків. Field
 * injection у абстрактному батьку — це легітимний виняток, який офіційно
 * підтримується Spring.
 */
@Slf4j
public abstract class BaseController {

    @Autowired
    protected UserRepository userRepository;

    /**
     * Отримує поточного користувача з {@link Authentication}.
     * <p>
     * Підтримує три типи principal-а:
     * <ol>
     *   <li>{@link CustomOAuth2User} — вхід через OAuth2 (Google/GitHub)</li>
     *   <li>{@link User} — локальний вхід (наш entity, який реалізує {@link UserDetails})</li>
     *   <li>будь-який інший {@link UserDetails} — fallback: завантаження з БД по username</li>
     * </ol>
     *
     * @return {@link User} або {@code null}, якщо користувач не автентифікований
     *         чи principal не розпізнано
     */
    protected User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        // 1. OAuth2 (Google / GitHub)
        if (principal instanceof CustomOAuth2User oAuth2User) {
            return oAuth2User.getUser();
        }

        // 2. Наш User entity (реалізує UserDetails) — локальний вхід
        if (principal instanceof User user) {
            return user;
        }

        // 3. Fallback: будь-який UserDetails (наприклад, @WithMockUser у тестах
        //    або майбутній спосіб автентифікації) — тягнемо з БД
        if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            User fromDb = userRepository.findByUsername(username).orElse(null);
            if (fromDb == null) {
                log.warn("Principal '{}' (UserDetails) не знайдено в БД — повертаємо null",
                        username);
            }
            return fromDb;
        }

        log.warn("Невідомий тип principal: {} — getCurrentUser повертає null",
                principal.getClass().getName());
        return null;
    }

    /**
     * Оновлює {@link SecurityContext} новими даними користувача.
     * Викликається після зміни профілю / валюти / мови, щоб зміни
     * відобразилися без перелогіну.
     * <p>
     * Використовує authorities з {@code updatedUser}, а не зі старого
     * {@code authentication} — це важливо, якщо роль могла змінитися.
     */
    protected void updateSecurityContext(Authentication authentication, User updatedUser) {
        Authentication newAuth = new UsernamePasswordAuthenticationToken(
                updatedUser,
                authentication.getCredentials(),
                updatedUser.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }

    /**
     * Редірект на попередню сторінку (заголовок Referer) або на {@code "/"} якщо його немає.
     */
    protected String redirectToReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            return "redirect:" + referer;
        }
        return "redirect:/";
    }
}