package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.Category;
import org.lazycent.model.User;
import org.lazycent.service.CategoryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AJAX/REST endpoints для категорій.
 * <p>
 * Відокремлений від {@link CategoryController} (який обслуговує форми) —
 * щоб можна було застосувати {@code @RestControllerAdvice} вибірково,
 * не ламаючи HTML-flow.
 * <p>
 * URL ті самі — {@code /categories/expense} і {@code /categories/income},
 * щоб не змінювати фронтенд.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class CategoryRestController extends BaseController {

    private final CategoryService categoryService;

    @GetMapping("/categories/expense")
    public List<Category> getExpenseCategoriesJson(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            // Захист: security-фільтр уже відсікає анонімів, але на всяк випадок
            // повертаємо порожній список замість null
            log.warn("AJAX /categories/expense без автентифікації");
            return List.of();
        }
        return categoryService.getExpenseCategories(currentUser);
    }

    @GetMapping("/categories/income")
    public List<Category> getIncomeCategoriesJson(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            log.warn("AJAX /categories/income без автентифікації");
            return List.of();
        }
        return categoryService.getIncomeCategories(currentUser);
    }
}