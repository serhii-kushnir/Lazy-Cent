package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.User;
import org.lazycent.service.BudgetService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class TransactionController extends BaseController {

    private final BudgetService budgetService;

    /**
     * Безпечний редірект. Дозволяє лише внутрішні шляхи (починаються з "/"),
     * блокує protocol-relative ("//evil.com"), absolute ("http://...") та CRLF.
     */
    private String safeReturnTo(String returnTo, String fallback) {
        if (returnTo == null || returnTo.isBlank()) return fallback;
        if (!returnTo.startsWith("/") || returnTo.startsWith("//")) return fallback;
        if (returnTo.contains("://") || returnTo.contains("\r") || returnTo.contains("\n")) return fallback;
        return returnTo;
    }

    // ==================== ДУБЛЮВАННЯ ====================

    @PostMapping("/expense/duplicate/{id}")
    public String duplicateExpense(@PathVariable Long id,
                                   @RequestParam(value = "returnTo", required = false) String returnTo,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        LocalDate date = budgetService.duplicateExpense(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", msg("toast.expense.duplicated"));
        return "redirect:" + safeReturnTo(returnTo, "/day/" + date);
    }

    @PostMapping("/income/duplicate/{id}")
    public String duplicateIncome(@PathVariable Long id,
                                  @RequestParam(value = "returnTo", required = false) String returnTo,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        LocalDate date = budgetService.duplicateIncome(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", msg("toast.income.duplicated"));
        return "redirect:" + safeReturnTo(returnTo, "/day/" + date);
    }

    // ==================== ВИДАЛЕННЯ ====================

    @PostMapping("/expense/delete/{id}")
    public String deleteExpense(@PathVariable Long id,
                                @RequestParam(value = "returnTo", required = false) String returnTo,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        LocalDate date = budgetService.getDateByExpense(id, currentUser);
        budgetService.deleteExpense(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", msg("toast.expense.deleted"));
        return "redirect:" + safeReturnTo(returnTo, "/day/" + date);
    }

    @PostMapping("/income/delete/{id}")
    public String deleteIncome(@PathVariable Long id,
                               @RequestParam(value = "returnTo", required = false) String returnTo,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        LocalDate date = budgetService.getDateByIncome(id, currentUser);
        budgetService.deleteIncome(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", msg("toast.income.deleted"));
        return "redirect:" + safeReturnTo(returnTo, "/day/" + date);
    }

    // ==================== РЕДАГУВАННЯ ====================

    @PostMapping("/expense/edit/{id}")
    public String editExpense(@PathVariable Long id,
                              @RequestParam Long categoryId,
                              @RequestParam String description,
                              @RequestParam Double amount,
                              @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
                              @RequestParam(value = "details", required = false) String details,
                              @RequestParam(value = "shareWithFamily", required = false, defaultValue = "false") Boolean shareWithFamily,
                              @RequestParam(value = "time", required = false) String timeStr,
                              @RequestParam(value = "buyer", required = false) String buyer,
                              @RequestParam(value = "seller", required = false) String seller,
                              @RequestParam(value = "returnTo", required = false) String returnTo,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        LocalTime time = (timeStr != null && !timeStr.isEmpty()) ? LocalTime.parse(timeStr) : null;
        budgetService.updateExpense(id, description, amount, categoryId, quantity, details,
                shareWithFamily, time, buyer, seller, currentUser);
        LocalDate date = budgetService.getDateByExpense(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", msg("toast.expense.updated"));
        return "redirect:" + safeReturnTo(returnTo, "/day/" + date);
    }

    @PostMapping("/income/edit/{id}")
    public String editIncome(@PathVariable Long id,
                             @RequestParam Long categoryId,
                             @RequestParam String description,
                             @RequestParam Double amount,
                             @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
                             @RequestParam(value = "details", required = false) String details,
                             @RequestParam(value = "shareWithFamily", required = false, defaultValue = "false") Boolean shareWithFamily,
                             @RequestParam(value = "time", required = false) String timeStr,
                             @RequestParam(value = "buyer", required = false) String buyer,
                             @RequestParam(value = "seller", required = false) String seller,
                             @RequestParam(value = "returnTo", required = false) String returnTo,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        LocalTime time = (timeStr != null && !timeStr.isEmpty()) ? LocalTime.parse(timeStr) : null;
        budgetService.updateIncome(id, description, amount, categoryId, quantity, details,
                shareWithFamily, time, buyer, seller, currentUser);
        LocalDate date = budgetService.getDateByIncome(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", msg("toast.income.updated"));
        return "redirect:" + safeReturnTo(returnTo, "/day/" + date);
    }

    // ==================== BULK ====================

    @PostMapping("/expense/bulk/delete")
    @ResponseBody
    public Map<String, Object> bulkDeleteExpenses(
            @RequestParam("ids") List<Long> ids,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            Authentication authentication) {
        User user = getCurrentUser(authentication);
        if (user == null) return Map.of("success", false, "error", "Unauthorized");
        try {
            int count = budgetService.bulkDeleteExpenses(ids, user);
            return Map.of("success", true, "count", count);
        } catch (Exception e) {
            log.error("bulkDeleteExpenses failed", e);
            return Map.of("success", false, "error", e.getMessage() != null ? e.getMessage() : "error");
        }
    }

    @PostMapping("/income/bulk/delete")
    @ResponseBody
    public Map<String, Object> bulkDeleteIncomes(
            @RequestParam("ids") List<Long> ids,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            Authentication authentication) {
        User user = getCurrentUser(authentication);
        if (user == null) return Map.of("success", false, "error", "Unauthorized");
        try {
            int count = budgetService.bulkDeleteIncomes(ids, user);
            return Map.of("success", true, "count", count);
        } catch (Exception e) {
            log.error("bulkDeleteIncomes failed", e);
            return Map.of("success", false, "error", e.getMessage() != null ? e.getMessage() : "error");
        }
    }

    @PostMapping("/expense/bulk/duplicate")
    @ResponseBody
    public Map<String, Object> bulkDuplicateExpenses(
            @RequestParam("ids") List<Long> ids,
            Authentication authentication) {
        User user = getCurrentUser(authentication);
        if (user == null) return Map.of("success", false, "error", "Unauthorized");
        try {
            int count = budgetService.bulkDuplicateExpenses(ids, user);
            return Map.of("success", true, "count", count);
        } catch (Exception e) {
            log.error("bulkDuplicateExpenses failed", e);
            return Map.of("success", false, "error", e.getMessage() != null ? e.getMessage() : "error");
        }
    }

    @PostMapping("/income/bulk/duplicate")
    @ResponseBody
    public Map<String, Object> bulkDuplicateIncomes(
            @RequestParam("ids") List<Long> ids,
            Authentication authentication) {
        User user = getCurrentUser(authentication);
        if (user == null) return Map.of("success", false, "error", "Unauthorized");
        try {
            int count = budgetService.bulkDuplicateIncomes(ids, user);
            return Map.of("success", true, "count", count);
        } catch (Exception e) {
            log.error("bulkDuplicateIncomes failed", e);
            return Map.of("success", false, "error", e.getMessage() != null ? e.getMessage() : "error");
        }
    }
}