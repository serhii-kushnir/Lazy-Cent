package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.User;
import org.lazycent.service.CategoryService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * MVC-контролер для CRUD категорій через HTML-форми.
 * <p>
 * AJAX endpoints ({@code /categories/expense}, {@code /categories/income})
 * винесені в {@link CategoryRestController}, щоб мати окрему обробку помилок
 * (JSON замість HTML-редіректу).
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class CategoryController extends BaseController {

    private final CategoryService categoryService;

    @PostMapping("/categories/add")
    public String addCategory(@RequestParam String name,
                              @RequestParam String type,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes,
                              HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            categoryService.addCategory(name, type, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Категорію додано!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectToReferer(request);
    }

    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable Long id,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            categoryService.deleteCategory(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Категорію видалено!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectToReferer(request);
    }

    @PostMapping("/categories/update/{id}")
    public String updateCategory(@PathVariable Long id,
                                 @RequestParam String name,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            categoryService.updateCategory(id, name, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Категорію оновлено!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectToReferer(request);
    }
}