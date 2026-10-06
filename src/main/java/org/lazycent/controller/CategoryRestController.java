package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.Category;
import org.lazycent.model.User;
import org.lazycent.model.dto.CategoryDto;
import org.lazycent.service.CategoryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class CategoryRestController extends BaseController {

    private final CategoryService categoryService;

    @GetMapping("/categories/expense")
    public List<CategoryDto> getExpenseCategoriesJson(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            log.warn("AJAX /categories/expense без автентифікації");
            return List.of();
        }
        return categoryService.getExpenseCategories(currentUser).stream()
                .map(CategoryDto::from)
                .toList();
    }

    @GetMapping("/categories/income")
    public List<CategoryDto> getIncomeCategoriesJson(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            log.warn("AJAX /categories/income без автентифікації");
            return List.of();
        }
        return categoryService.getIncomeCategories(currentUser).stream()
                .map(CategoryDto::from)
                .toList();
    }
}