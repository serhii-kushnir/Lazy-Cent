package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.Category;
import org.lazycent.model.User;
import org.lazycent.repository.CategoryRepository;
import org.lazycent.repository.ExpenseRepository;
import org.lazycent.repository.IncomeRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final MessageSource messageSource;

    // ==================== ЧИТАННЯ (з кешем) ====================

    @Cacheable(value = "expenseCategories", key = "#user.id")
    public List<Category> getExpenseCategories(User user) {
        log.debug("DB query: getExpenseCategories for user {}", user.getUsername());
        return categoryRepository.findByTypeAndOwner("EXPENSE", user);
    }

    @Cacheable(value = "incomeCategories", key = "#user.id")
    public List<Category> getIncomeCategories(User user) {
        log.debug("DB query: getIncomeCategories for user {}", user.getUsername());
        return categoryRepository.findByTypeAndOwner("INCOME", user);
    }

    public List<Category> getAllCategories(User user) {
        return categoryRepository.findAllByOwner(user);
    }

    public long countByOwner(User user) {
        return categoryRepository.countByOwner(user);
    }

    public Category getCategoryById(Long id, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Категорію", id));
        if (!category.getOwner().equals(user)) {
            throw new AccessDeniedException(msg("error.access.denied"));
        }
        return category;
    }

    public Category getCategoryByIdAndType(Long id, User user, String expectedType) {
        Category category = getCategoryById(id, user);
        if (!expectedType.equalsIgnoreCase(category.getType())) {
            throw new ValidationException(msg(
                    "error.category.type.mismatch",
                    expectedType, category.getType()));
        }
        return category;
    }

    public long countUsages(Category category, User user) {
        long expenses = expenseRepository.countByCategoryAndOwner(category, user);
        long incomes = incomeRepository.countByCategoryAndOwner(category, user);
        return expenses + incomes;
    }

    // ==================== СТВОРЕННЯ ====================

    /**
     * Створює нову категорію.
     *
     * <p><b>Захист від race condition:</b> попередня перевірка
     * {@code findByNameIgnoreCaseAndOwner} + {@code save} не атомарні.
     * Два паралельні запити можуть обидва пройти перевірку й обидва
     * вставити рядок. Unique index {@code uk_category_user_name}
     * (V5) гарантує, що другий INSERT впаде з
     * {@link DataIntegrityViolationException}, яку ми перетворюємо
     * у зрозумілу {@link ValidationException}.
     */
    @Transactional
    @CacheEvict(value = {"expenseCategories", "incomeCategories"}, key = "#user.id")
    public Category addCategory(String name, String type, User user) {
        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            throw new ValidationException(msg("error.category.name.empty"));
        }
        if (categoryRepository.findByNameIgnoreCaseAndOwner(normalizedName, user).isPresent()) {
            throw new ValidationException(msg("error.category.name.exists"));
        }
        Category category = new Category();
        category.setName(normalizedName);
        category.setType(type.toUpperCase());
        category.setOwner(user);
        try {
            return categoryRepository.save(category);
        } catch (DataIntegrityViolationException e) {
            // Race condition: між перевіркою вище і save хтось устиг
            // вставити категорію з такою самою назвою.
            log.warn("Race condition при створенні категорії '{}' для користувача {}",
                    normalizedName, user.getUsername());
            throw new ValidationException(msg("error.category.name.exists"));
        }
    }

    // ==================== ОНОВЛЕННЯ ====================

    @Transactional
    @CacheEvict(value = {"expenseCategories", "incomeCategories"}, key = "#user.id")
    public Category updateCategory(Long id, String newName, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Категорію", id));
        if (!category.getOwner().equals(user)) {
            throw new AccessDeniedException(msg("error.access.denied"));
        }
        String normalizedName = newName.trim();
        if (normalizedName.isEmpty()) {
            throw new ValidationException(msg("error.category.name.empty"));
        }
        categoryRepository.findByNameIgnoreCaseAndOwner(normalizedName, user)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new ValidationException(msg("error.category.name.exists"));
                    }
                });
        category.setName(normalizedName);
        try {
            return categoryRepository.save(category);
        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition при оновленні категорії id={} для користувача {}",
                    id, user.getUsername());
            throw new ValidationException(msg("error.category.name.exists"));
        }
    }

    // ==================== ВИДАЛЕННЯ ====================

    @Transactional
    @CacheEvict(value = {"expenseCategories", "incomeCategories"}, key = "#user.id")
    public void deleteCategory(Long id, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Категорію", id));
        if (!category.getOwner().equals(user)) {
            throw new AccessDeniedException(msg("error.access.denied"));
        }

        long usages = countUsages(category, user);
        if (usages > 0) {
            throw new ValidationException(msg("error.category.inUse", usages));
        }

        categoryRepository.deleteById(id);
        log.info("Категорію '{}' (id={}) видалено користувачем {}",
                category.getName(), id, user.getUsername());
    }

    @Transactional
    @CacheEvict(value = {"expenseCategories", "incomeCategories"}, key = "#user.id")
    public void deleteAllCategories(User user) {
        List<Category> categories = categoryRepository.findAllByOwner(user);
        if (!categories.isEmpty()) {
            categoryRepository.deleteAll(categories);
            categoryRepository.flush();
        }
    }

    // ==================== ДОПОМІЖНІ ====================

    private String msg(String key, Object... args) {
        try {
            return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            log.warn("Не знайдено переклад для ключа '{}': {}", key, e.getMessage());
            return key;
        }
    }
}