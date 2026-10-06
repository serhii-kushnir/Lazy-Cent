package org.lazycent.model.dto;

import org.lazycent.model.Category;

/**
 * Публічне представлення категорії для AJAX-клієнта.
 *
 * <p>Не містить {@code owner} — навіщо клієнту знати про власника
 * категорії, якщо він і так автентифікований і бачить тільки свої?
 * Без цього DTO Jackson серіалізував би весь {@code User} (username,
 * email, role, provider, providerId) — зайве й небезпечно.
 */
public record CategoryDto(Long id, String name, String type) {

    public static CategoryDto from(Category category) {
        return new CategoryDto(category.getId(), category.getName(), category.getType());
    }
}