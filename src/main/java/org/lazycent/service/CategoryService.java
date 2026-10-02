package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.Category;
import org.lazycent.model.User;
import org.lazycent.repository.CategoryRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервіс для роботи з категоріями.
 * Містить всю логіку CRUD категорій, яка раніше була розкидана між BudgetService,
 * BudgetPlanningService та DemoDataService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // ==================== ЧИТАННЯ ====================

    public List<Category> getAllCategories(User user) {
        return categoryRepository.findAllByOwner(user);
    }

    public List<Category> getExpenseCategories(User user) {
        return categoryRepository.findByTypeAndOwner("EXPENSE", user);
    }

    public List<Category> getIncomeCategories(User user) {
        return categoryRepository.findByTypeAndOwner("INCOME", user);
    }

    public long countByOwner(User user) {
        return categoryRepository.countByOwner(user);
    }

    public Category getCategoryById(Long id, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Категорію", id));
        if (!category.getOwner().equals(user)) {
            throw new AccessDeniedException("Ця категорія не належить вам");
        }
        return category;
    }

    // ==================== СТВОРЕННЯ ====================

    @Transactional
    public Category addCategory(String name, String type, User user) {
        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            throw new ValidationException("Назва категорії не може бути порожньою");
        }
        if (categoryRepository.findByNameIgnoreCaseAndOwner(normalizedName, user).isPresent()) {
            throw new ValidationException("Категорія з таким ім'ям вже існує у вас");
        }
        Category category = new Category();
        category.setName(normalizedName);
        category.setType(type.toUpperCase());
        category.setOwner(user);
        return categoryRepository.save(category);
    }

    /**
     * Створює категорію, якщо її ще немає (для демо-даних).
     */
    @Transactional
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
    public Category updateCategory(Long id, String newName, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Категорію", id));
        if (!category.getOwner().equals(user)) {
            throw new AccessDeniedException("Ця категорія не належить вам");
        }
        String normalizedName = newName.trim();
        if (normalizedName.isEmpty()) {
            throw new ValidationException("Назва категорії не може бути порожньою");
        }
        categoryRepository.findByNameIgnoreCaseAndOwner(normalizedName, user)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new ValidationException("Категорія з таким ім'ям вже існує у вас");
                    }
                });
        category.setName(normalizedName);
        return categoryRepository.save(category);
    }

    // ==================== ВИДАЛЕННЯ ====================

    @Transactional
    public void deleteCategory(Long id, User user) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Категорію", id));
        if (!category.getOwner().equals(user)) {
            throw new AccessDeniedException("Ця категорія не належить вам");
        }
        categoryRepository.deleteById(id);
    }

    @Transactional
    public void deleteAllCategories(User user) {
        List<Category> categories = categoryRepository.findAllByOwner(user);
        if (!categories.isEmpty()) {
            categoryRepository.deleteAll(categories);
        }
    }
}