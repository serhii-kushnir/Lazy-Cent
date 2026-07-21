// controller/BudgetController.java
package org.webmoney.controller;

import org.webmoney.model.*;
import org.webmoney.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class BudgetController {

    private static final Logger log = LoggerFactory.getLogger(BudgetController.class);
    private final BudgetService budgetService;

    @GetMapping("/")
    public String index(Model model) {
        log.info("Accessing index page");
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("expenseCategories", Expense.ExpenseCategory.values());
        model.addAttribute("incomeCategories", Income.IncomeCategory.values());
        return "index";
    }

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
            model.addAttribute("expenseCategories", Expense.ExpenseCategory.values());
            model.addAttribute("incomeCategories", Income.IncomeCategory.values());

            return "budget";
        } catch (Exception e) {
            log.error("Error loading budget page: ", e);
            model.addAttribute("error", "Помилка завантаження бюджету: " + e.getMessage());
            return "error";
        }
    }

    @GetMapping("/test")
    @ResponseBody
    public String test() {
        return "Application is working!";
    }

    @GetMapping("/simple")
    public String simple(Model model) {
        model.addAttribute("message", "Hello World!");
        return "simple";
    }

    @GetMapping("/day/{date}")
    public String viewDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        try {
            DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
            model.addAttribute("dayBudget", dayBudget);
            model.addAttribute("expenseCategories", Expense.ExpenseCategory.values());
            model.addAttribute("incomeCategories", Income.IncomeCategory.values());
            return "day-detail";
        } catch (Exception e) {
            log.error("Error loading day page: ", e);
            model.addAttribute("error", "Помилка завантаження дня: " + e.getMessage());
            return "error";
        }
    }

    @PostMapping("/day/{date}/expense")
    public String addExpense(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Expense.ExpenseCategory category,
            @RequestParam String description,
            @RequestParam Double amount) {
        try {
            DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
            Expense expense = new Expense();
            expense.setCategory(category);
            expense.setDescription(description);
            expense.setAmount(amount);
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
            @RequestParam Income.IncomeCategory category,
            @RequestParam String description,
            @RequestParam Double amount) {
        try {
            DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
            Income income = new Income();
            income.setCategory(category);
            income.setDescription(description);
            income.setAmount(amount);
            budgetService.addIncome(dayBudget.getId(), income);
            return "redirect:/day/" + date;
        } catch (Exception e) {
            log.error("Error adding income: ", e);
            return "redirect:/day/" + date + "?error=true";
        }
    }

    @PostMapping("/init-demo")
    public String initDemoData(Model model) {
        log.info("Initializing demo data");
        try {
            budgetService.initializeDemoData();
            return "redirect:/budget";
        } catch (Exception e) {
            log.error("Error initializing demo data: ", e);
            model.addAttribute("error", "Помилка ініціалізації даних: " + e.getMessage());
            return "error";
        }
    }

    @GetMapping("/init-demo-test")
    @ResponseBody
    public String initDemoTest() {
        try {
            budgetService.initializeDemoData();
            return "Demo data initialized successfully! Go to /budget to see results.";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Додайте ці методи в BudgetController.java

    @PostMapping("/quick-expense")
    public String quickAddExpense(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam("category") Expense.ExpenseCategory category,
            @RequestParam("description") String description,
            @RequestParam("amount") Double amount) {

        log.info("Quick add expense: date={}, category={}, description={}, amount={}",
                date, category, description, amount);

        try {
            DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
            Expense expense = new Expense();
            expense.setCategory(category);
            expense.setDescription(description);
            expense.setAmount(amount);
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
            @RequestParam("category") Income.IncomeCategory category,
            @RequestParam("description") String description,
            @RequestParam("amount") Double amount) {

        log.info("Quick add income: date={}, category={}, description={}, amount={}",
                date, category, description, amount);

        try {
            DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
            Income income = new Income();
            income.setCategory(category);
            income.setDescription(description);
            income.setAmount(amount);
            budgetService.addIncome(dayBudget.getId(), income);
            return "redirect:/budget";
        } catch (Exception e) {
            log.error("Error in quick income: ", e);
            return "redirect:/?error=income_failed";
        }
    }

    @GetMapping("/totals")
    public String viewtotals(
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
            model.addAttribute("error", "Помилка завантаження дашборду: " + e.getMessage());
            return "error";
        }
    }


    @GetMapping("/all-days")
    public String viewAllDays(Model model) {
        log.info("Viewing all days");
        try {
            List<DayBudget> allBudgets = budgetService.getAllBudgets();
            model.addAttribute("allBudgets", allBudgets);
            return "all-days";
        } catch (Exception e) {
            log.error("Error loading all days: ", e);
            model.addAttribute("error", "Помилка завантаження: " + e.getMessage());
            return "error";
        }
    }
    
}