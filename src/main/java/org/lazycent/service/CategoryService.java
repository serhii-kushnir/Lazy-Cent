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

    /**
     * Знаходить категорію за ID і власником, додатково перевіряючи тип.
     *
     * <p>Захист від розсинхрону: якщо хтось передасть INCOME-категорію
     * у Expense-транзакцію — буде {@link ValidationException}, а не тихе
     * «ну ок, запишемо як витрату».
     *
     * @param id           ID категорії
     * @param user         власник
     * @param expectedType очікуваний тип — {@code "EXPENSE"} або {@code "INCOME"}
     * @throws EntityNotFoundException якщо категорії немає
     * @throws AccessDeniedException   якщо категорія не належить користувачу
     * @throws ValidationException     якщо тип категорії не збігається
     */
    public Category getCategoryByIdAndType(Long id, User user, String expectedType) {
        Category category = getCategoryById(id, user);
        if (!expectedType.equalsIgnoreCase(category.getType())) {
            throw new ValidationException(msg(
                    "error.category.type.mismatch",
                    expectedType, category.getType()));
        }
        return category;
    }

    /**
     * Перевіряє, чи категорія використовується у транзакціях.
     *
     * @return сумарна кількість витрат і доходів у цій категорії
     */
    public long countUsages(Category category, User user) {
        long expenses = expenseRepository.countByCategoryAndOwner(category, user);
        long incomes = incomeRepository.countByCategoryAndOwner(category, user);
        return expenses + incomes;
    }

    // ==================== СТВОРЕННЯ ====================

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
        return categoryRepository.save(category);
    }

    @Transactional
    @CacheEvict(value = {"expenseCategories", "incomeCategories"}, key = "#user.id")
    public Category createIfNotExists(String name, String type, User user) {
        return categoryRepository.findByNameAndOwner(name, user)
                .orElseGet(() -> {
                    Category cat = new Category();
                    cat.setName(name);
                    cat.setType(type);
                    cat.setOwner(user);
                    return categoryRepository.save(cat);
                });
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
        return categoryRepository.save(category);
    }

    // ==================== ВИДАЛЕННЯ ====================

    /**
     * Видаляє категорію, якщо вона не використовується в жодній транзакції.
     *
     * <p><b>Чому pre-check:</b> FK-констрейнт у БД теж спрацює, але:
     * <ul>
     *   <li>повідомлення від БД неспецифічне — незрозуміло, скільки
     *       транзакцій блокує видалення;</li>
     *   <li>pre-check дає змогу віддати {@link ValidationException}
     *       (400) замість {@link org.springframework.dao.DataIntegrityViolationException}
     *       (яку {@code GlobalExceptionHandler} мапить у загальне
     *       «Можливо, ви намагаєтесь видалити категорію...»);</li>
     *   <li>швидше — без rollback-у транзакції.</li>
     * </ul>
     *
     * <p>Race condition між pre-check і delete (хтось додав транзакцію
     * паралельно) все ще можливий — його ловить FK і
     * {@code DataIntegrityViolationException} handler як fallback.
     */
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
            // Для clearAllUserData — спочатку видаляються DayBudget
            // (каскадно з Expense/Income), потім категорії. Тому
            // pre-check тут не потрібен.
            categoryRepository.deleteAll(categories);
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