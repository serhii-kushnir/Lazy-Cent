package org.finnest.controller;

import org.finnest.model.*;
import org.finnest.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class BudgetController {

    private static final Logger log = LoggerFactory.getLogger(BudgetController.class);
    private final BudgetService budgetService;

    // ==================== ДУБЛЮВАННЯ ТРАНЗАКЦІЙ ====================

    @PostMapping("/expense/duplicate/{id}")
    public String duplicateExpense(@PathVariable Long id) {
        try {
            LocalDate date = budgetService.duplicateExpense(id);
            return "redirect:/day/" + date;
        } catch (Exception e) {
            log.error("Error duplicating expense: ", e);
            return "redirect:/?error=duplicate_failed";
        }
    }

    @PostMapping("/income/duplicate/{id}")
    public String duplicateIncome(@PathVariable Long id) {
        try {
            LocalDate date = budgetService.duplicateIncome(id);
            return "redirect:/day/" + date;
        } catch (Exception e) {
            log.error("Error duplicating income: ", e);
            return "redirect:/?error=duplicate_failed";
        }
    }

    // ==================== ГОЛОВНА ====================
    @GetMapping("/")
    public String index(Model model) {
        log.info("Accessing index page");
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
        return "index";
    }

    // ==================== БЮДЖЕТ ====================
    @GetMapping("/budget")
    public String viewBudget(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {
        log.info("Accessing budget page");
        try {
            if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
            if (endDate == null) endDate = LocalDate.now();

            TotalBudget totalBudget = budgetService.getTotalBudget(startDate, endDate);
            if (totalBudget == null) {
                totalBudget = new TotalBudget();
                totalBudget.setTotalExpenses(0.0);
                totalBudget.setTotalIncomes(0.0);
                totalBudget.setBalance(0.0);
                totalBudget.setExpensesByCategory(new HashMap<>());
                totalBudget.setIncomesByCategory(new HashMap<>());
                totalBudget.setDailySummaries(new java.util.LinkedHashMap<>());
            }

            model.addAttribute("totalBudget", totalBudget);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
            model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
            return "budget";
        } catch (Exception e) {
            log.error("Error loading budget page: ", e);
            model.addAttribute("error", "Помилка завантаження бюджету: " + e.getMessage());
            return "error";
        }
    }

    // ==================== ДЕНЬ ====================
    @GetMapping("/day/{date}")
    public String viewDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        try {
            DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
            model.addAttribute("dayBudget", dayBudget);
            model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
            model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
            return "day-detail";
        } catch (Exception e) {
            log.error("Error loading day page: ", e);
            model.addAttribute("error", "Помилка завантаження дня: " + e.getMessage());
            return "error";
        }
    }

    // ==================== ВСІ ДНІ ====================
    @GetMapping("/all-days")
    public String viewAllDays(Model model) {
        log.info("Viewing all days");
        try {
            List<DayBudget> allBudgets = budgetService.getAllBudgets();
            model.addAttribute("allBudgets", allBudgets);
            model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
            model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
            return "all-days";
        } catch (Exception e) {
            log.error("Error loading all days: ", e);
            model.addAttribute("error", "Помилка завантаження: " + e.getMessage());
            return "error";
        }
    }

    // ==================== ПІДСУМКИ ====================
    @GetMapping("/totals")
    public String viewTotals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {
        log.info("Accessing totals page");
        try {
            if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
            if (endDate == null) endDate = LocalDate.now();

            TotalBudget totalBudget = budgetService.getTotalBudget(startDate, endDate);
            if (totalBudget == null) {
                totalBudget = new TotalBudget();
                totalBudget.setTotalExpenses(0.0);
                totalBudget.setTotalIncomes(0.0);
                totalBudget.setBalance(0.0);
                totalBudget.setExpensesByCategory(new HashMap<>());
                totalBudget.setIncomesByCategory(new HashMap<>());
                totalBudget.setDailySummaries(new java.util.LinkedHashMap<>());
            }

            model.addAttribute("totalBudget", totalBudget);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            return "totals";
        } catch (Exception e) {
            log.error("Error loading totals page: ", e);
            model.addAttribute("error", "Помилка завантаження підсумків: " + e.getMessage());
            return "error";
        }
    }

    // ==================== НАЛАШТУВАННЯ ====================
    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("categories", budgetService.getAllCategories());
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
        return "settings";
    }

    // ==================== ДОДАВАННЯ ТРАНЗАКЦІЙ ====================
    @PostMapping("/day/{date}/expense")
    public String addExpense(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Long categoryId,
            @RequestParam String description,
            @RequestParam Double amount,
            @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
            @RequestParam(value = "details", required = false) String details,
            @RequestParam(value = "shareWithFamily", required = false, defaultValue = "false") Boolean shareWithFamily,
            @RequestParam(value = "time", required = false) String timeStr,
            @RequestParam(value = "buyer", required = false) String buyer,
            @RequestParam(value = "seller", required = false) String seller) {
        try {
            DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
            Category category = budgetService.getCategoryById(categoryId);
            Expense expense = new Expense();
            expense.setCategory(category);
            expense.setDescription(description);
            expense.setAmount(amount);
            expense.setQuantity(quantity != null ? quantity : 1);
            expense.setDetails(details);
            expense.setShareWithFamily(shareWithFamily != null && shareWithFamily);
            if (timeStr != null && !timeStr.isEmpty()) {
                expense.setTime(LocalTime.parse(timeStr));
            }
            expense.setBuyer(buyer);
            expense.setSeller(seller);
            budgetService.addExpense(dayBudget.getId(), expense);
            return "redirect:/day/" + date;
        } catch (Exception e) {
            log.error("Error adding expense: ", e);
            return "redirect:/day/" + date + "?error=true";
        }
    }

    @PostMapping("/day/{date}/income")
    public String addIncome(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Long categoryId,
            @RequestParam String description,
            @RequestParam Double amount,
            @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
            @RequestParam(value = "details", required = false) String details,
            @RequestParam(value = "shareWithFamily", required = false, defaultValue = "false") Boolean shareWithFamily,
            @RequestParam(value = "time", required = false) String timeStr,
            @RequestParam(value = "buyer", required = false) String buyer,
            @RequestParam(value = "seller", required = false) String seller) {
        try {
            DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
            Category category = budgetService.getCategoryById(categoryId);
            Income income = new Income();
            income.setCategory(category);
            income.setDescription(description);
            income.setAmount(amount);
            income.setQuantity(quantity != null ? quantity : 1);
            income.setDetails(details);
            income.setShareWithFamily(shareWithFamily != null && shareWithFamily);
            if (timeStr != null && !timeStr.isEmpty()) {
                income.setTime(LocalTime.parse(timeStr));
            }
            income.setBuyer(buyer);
            income.setSeller(seller);
            budgetService.addIncome(dayBudget.getId(), income);
            return "redirect:/day/" + date;
        } catch (Exception e) {
            log.error("Error adding income: ", e);
            return "redirect:/day/" + date + "?error=true";
        }
    }

    @PostMapping("/quick-expense")
    public String quickAddExpense(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam("category") Long categoryId,
            @RequestParam("description") String description,
            @RequestParam("amount") Double amount,
            @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
            @RequestParam(value = "details", required = false) String details,
            @RequestParam(value = "shareWithFamily", required = false, defaultValue = "false") Boolean shareWithFamily,
            @RequestParam(value = "time", required = false) String timeStr,
            @RequestParam(value = "buyer", required = false) String buyer,
            @RequestParam(value = "seller", required = false) String seller) {
        try {
            DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
            Category category = budgetService.getCategoryById(categoryId);
            Expense expense = new Expense();
            expense.setCategory(category);
            expense.setDescription(description);
            expense.setAmount(amount);
            expense.setQuantity(quantity != null ? quantity : 1);
            expense.setDetails(details);
            expense.setShareWithFamily(shareWithFamily != null && shareWithFamily);
            if (timeStr != null && !timeStr.isEmpty()) {
                expense.setTime(LocalTime.parse(timeStr));
            }
            expense.setBuyer(buyer);
            expense.setSeller(seller);
            budgetService.addExpense(dayBudget.getId(), expense);
            return "redirect:/budget";
        } catch (Exception e) {
            log.error("Error in quick expense: ", e);
            return "redirect:/?error=expense_failed";
        }
    }

    @PostMapping("/quick-income")
    public String quickAddIncome(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam("category") Long categoryId,
            @RequestParam("description") String description,
            @RequestParam("amount") Double amount,
            @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
            @RequestParam(value = "details", required = false) String details,
            @RequestParam(value = "shareWithFamily", required = false, defaultValue = "false") Boolean shareWithFamily,
            @RequestParam(value = "time", required = false) String timeStr,
            @RequestParam(value = "buyer", required = false) String buyer,
            @RequestParam(value = "seller", required = false) String seller) {
        try {
            DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
            Category category = budgetService.getCategoryById(categoryId);
            Income income = new Income();
            income.setCategory(category);
            income.setDescription(description);
            income.setAmount(amount);
            income.setQuantity(quantity != null ? quantity : 1);
            income.setDetails(details);
            income.setShareWithFamily(shareWithFamily != null && shareWithFamily);
            if (timeStr != null && !timeStr.isEmpty()) {
                income.setTime(LocalTime.parse(timeStr));
            }
            income.setBuyer(buyer);
            income.setSeller(seller);
            budgetService.addIncome(dayBudget.getId(), income);
            return "redirect:/budget";
        } catch (Exception e) {
            log.error("Error in quick income: ", e);
            return "redirect:/?error=income_failed";
        }
    }

    // ==================== РЕДАГУВАННЯ / ВИДАЛЕННЯ ====================
    @PostMapping("/expense/delete/{id}")
    public String deleteExpense(@PathVariable Long id) {
        try {
            LocalDate date = budgetService.getDateByExpense(id);
            budgetService.deleteExpense(id);
            return "redirect:/day/" + date;
        } catch (Exception e) {
            log.error("Error deleting expense: ", e);
            return "redirect:/?error=delete_failed";
        }
    }

    @PostMapping("/income/delete/{id}")
    public String deleteIncome(@PathVariable Long id) {
        try {
            LocalDate date = budgetService.getDateByIncome(id);
            budgetService.deleteIncome(id);
            return "redirect:/day/" + date;
        } catch (Exception e) {
            log.error("Error deleting income: ", e);
            return "redirect:/?error=delete_failed";
        }
    }

    @PostMapping("/expense/edit/{id}")
    public String editExpense(
            @PathVariable Long id,
            @RequestParam Long categoryId,
            @RequestParam String description,
            @RequestParam Double amount,
            @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
            @RequestParam(value = "details", required = false) String details,
            @RequestParam(value = "shareWithFamily", required = false, defaultValue = "false") Boolean shareWithFamily,
            @RequestParam(value = "time", required = false) String timeStr,
            @RequestParam(value = "buyer", required = false) String buyer,
            @RequestParam(value = "seller", required = false) String seller) {
        try {
            LocalTime time = null;
            if (timeStr != null && !timeStr.isEmpty()) {
                time = LocalTime.parse(timeStr);
            }
            budgetService.updateExpense(id, description, amount, categoryId, quantity, details, shareWithFamily, time, buyer, seller);
            LocalDate date = budgetService.getDateByExpense(id);
            return "redirect:/day/" + date;
        } catch (Exception e) {
            log.error("Error editing expense: ", e);
            return "redirect:/?error=edit_failed";
        }
    }

    @PostMapping("/income/edit/{id}")
    public String editIncome(
            @PathVariable Long id,
            @RequestParam Long categoryId,
            @RequestParam String description,
            @RequestParam Double amount,
            @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
            @RequestParam(value = "details", required = false) String details,
            @RequestParam(value = "shareWithFamily", required = false, defaultValue = "false") Boolean shareWithFamily,
            @RequestParam(value = "time", required = false) String timeStr,
            @RequestParam(value = "buyer", required = false) String buyer,
            @RequestParam(value = "seller", required = false) String seller) {
        try {
            LocalTime time = null;
            if (timeStr != null && !timeStr.isEmpty()) {
                time = LocalTime.parse(timeStr);
            }
            budgetService.updateIncome(id, description, amount, categoryId, quantity, details, shareWithFamily, time, buyer, seller);
            LocalDate date = budgetService.getDateByIncome(id);
            return "redirect:/day/" + date;
        } catch (Exception e) {
            log.error("Error editing income: ", e);
            return "redirect:/?error=edit_failed";
        }
    }

    // ==================== КАТЕГОРІЇ ====================
    @PostMapping("/categories/add")
    public String addCategory(@RequestParam String name, @RequestParam String type) {
        try {
            budgetService.addCategory(name, type);
            return "redirect:/settings";
        } catch (Exception e) {
            log.error("Error adding category: ", e);
            return "redirect:/settings?error=category_add_failed";
        }
    }

    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable Long id) {
        try {
            budgetService.deleteCategory(id);
            return "redirect:/settings";
        } catch (Exception e) {
            log.error("Error deleting category: ", e);
            return "redirect:/settings?error=category_delete_failed";
        }
    }

    @PostMapping("/categories/update/{id}")
    public String updateCategory(@PathVariable Long id, @RequestParam String name) {
        try {
            budgetService.updateCategory(id, name);
            return "redirect:/settings";
        } catch (Exception e) {
            log.error("Error updating category: ", e);
            return "redirect:/settings?error=category_update_failed";
        }
    }

    // ==================== ЕКСПОРТ В EXCEL ====================
    // (тут ваш метод exportDayToExcel, який був раніше)

    // ==================== ТЕСТ ====================
    @GetMapping("/init-demo-test")
    @ResponseBody
    public String initDemoTest() {
        try {
            budgetService.initializeDemoData();
            return "Demo data initialized successfully!";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ==================== AJAX ДЛЯ КАТЕГОРІЙ ====================
    @GetMapping("/categories/expense")
    @ResponseBody
    public List<Category> getExpenseCategoriesJson() {
        return budgetService.getExpenseCategories();
    }

    @GetMapping("/categories/income")
    @ResponseBody
    public List<Category> getIncomeCategoriesJson() {
        return budgetService.getIncomeCategories();
    }
}