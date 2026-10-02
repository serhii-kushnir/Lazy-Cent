package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.*;
import org.lazycent.model.dto.BudgetDashboardDto;
import org.lazycent.model.dto.TransactionRequest;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.BudgetPlanningService;
import org.lazycent.service.BudgetService;
import org.lazycent.service.CategoryService;
import org.lazycent.service.DemoDataService;
import org.lazycent.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BudgetController {

    private final BudgetService budgetService;
    private final BudgetPlanningService budgetPlanningService;
    private final CategoryService categoryService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final DemoDataService demoDataService;
    private final DayBudgetRepository dayBudgetRepository;

    // ==================== ГОЛОВНА ====================
// ==================== ГОЛОВНА ====================

    /**
     * "/" — лендінг для анонімних, редірект на "/home" для залогінених.
     */
    @GetMapping("/")
    public String index(Authentication authentication) {
        if (getCurrentUser(authentication) != null) {
            return "redirect:/home";
        }
        return "index";
    }

    /**
     * "/home" — дашборд для залогінених, редірект на "/login" для анонімних.
     * (Spring Security і так не пустить аноніма сюди, але редірект — на випадок прямого переходу.)
     */
    @GetMapping("/home")
    public String home(Authentication authentication, Model model) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        long dayBudgetCount = dayBudgetRepository.countByOwner(currentUser);
        boolean showDemoBanner = (dayBudgetCount == 0);

        model.addAttribute("today", LocalDate.now());
        model.addAttribute("expenseCategories", categoryService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", categoryService.getIncomeCategories(currentUser));

        TotalBudget todaySummary = budgetService.getTodaySummary(currentUser);
        TotalBudget weekSummary = budgetService.getWeekSummary(currentUser);
        TotalBudget monthSummary = budgetService.getMonthSummary(currentUser);

        model.addAttribute("todaySummary", todaySummary != null ? todaySummary : new TotalBudget());
        model.addAttribute("weekSummary", weekSummary != null ? weekSummary : new TotalBudget());
        model.addAttribute("monthSummary", monthSummary != null ? monthSummary : new TotalBudget());
        model.addAttribute("showDemoBanner", showDemoBanner);

        return "home";
    }

    // ==================== ВАЛЮТА ====================

    @PostMapping("/settings/currency")
    public String updateCurrency(@RequestParam String currency,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            userService.updateCurrency(currentUser, currency);
            User updatedUser = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));
            updateSecurityContext(authentication, updatedUser);
            log.info("Валюта оновлена на {} для користувача {}", currency, updatedUser.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Валюту змінено на " + currency);
        } catch (Exception e) {
            log.error("Помилка зміни валюти: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка: " + e.getMessage());
        }

        return redirectToReferer(request);
    }

    // ==================== ПРОФІЛЬ ====================

    @PostMapping("/settings/profile")
    public String updateProfile(@RequestParam String username,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String currentPassword,
                                @RequestParam(required = false) String newPassword,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes,
                                HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            userService.updateProfile(currentUser, username, email, currentPassword, newPassword);
            User updatedUser = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));
            updateSecurityContext(authentication, updatedUser);
            redirectAttributes.addFlashAttribute("successMessage", "Профіль успішно оновлено!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return redirectToReferer(request);
    }

    @PostMapping("/settings/clear-data")
    public String clearData(Authentication authentication, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            budgetService.clearAllUserData(currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Всі дані успішно видалено!");
        } catch (Exception e) {
            log.error("Помилка при очищенні даних: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка при очищенні даних: " + e.getMessage());
        }

        return redirectToReferer(request);
    }

    @PostMapping("/settings/delete-account")
    public String deleteAccount(Authentication authentication,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            userService.deleteAccount(currentUser);
            request.getSession().invalidate();
            SecurityContextHolder.clearContext();
            redirectAttributes.addFlashAttribute("successMessage", "Акаунт успішно видалено!");
            return "redirect:/login";
        } catch (Exception e) {
            log.error("Помилка при видаленні акаунта: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка при видаленні акаунта: " + e.getMessage());
            return "redirect:/";
        }
    }

    // ==================== ДЕМО-ДАНІ ====================

    @PostMapping("/generate-demo")
    public String generateDemo(Authentication authentication, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            demoDataService.createDefaultCategories(currentUser);
            demoDataService.createRandomTransactions(currentUser, 14);
            redirectAttributes.addFlashAttribute("successMessage", "Демо-дані успішно додано! 🎉");
        } catch (Exception e) {
            log.error("Помилка генерації демо-даних: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка: " + e.getMessage());
        }

        return redirectToReferer(request);
    }

    // ==================== БЮДЖЕТ ====================

    @GetMapping("/budget")
    public String viewBudget(@RequestParam(value = "month", required = false) Integer month,
                             @RequestParam(value = "year", required = false) Integer year,
                             Authentication authentication,
                             Model model) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        if (month == null) month = LocalDate.now().getMonthValue();
        if (year == null) year = LocalDate.now().getYear();

        BudgetDashboardDto dashboard = budgetPlanningService.getDashboard(user, month, year);

        Map<String, Double> categoryExpenses = new LinkedHashMap<>();
        if (dashboard.getCategoryExpenses() != null) {
            for (Map.Entry<Category, Double> entry : dashboard.getCategoryExpenses().entrySet()) {
                if (entry.getValue() > 0) {
                    categoryExpenses.put(entry.getKey().getName(), entry.getValue());
                }
            }
        }

        model.addAttribute("dashboard", dashboard);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("expenseCategories", categoryService.getExpenseCategories(user));
        model.addAttribute("categoryExpenses", categoryExpenses);

        return "budget";
    }

    @PostMapping("/budget/plan")
    public String saveBudgetPlan(@RequestParam Double totalBudget,
                                 @RequestParam("month") Integer month,
                                 @RequestParam("year") Integer year,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        budgetPlanningService.saveOrUpdateBudgetPlan(currentUser, month, year, totalBudget);
        redirectAttributes.addFlashAttribute("successMessage", "Бюджет збережено!");
        return "redirect:/budget?month=" + month + "&year=" + year;
    }

    @PostMapping("/budget/category-limit")
    public String saveCategoryLimit(@RequestParam Long categoryId,
                                    @RequestParam Double limitAmount,
                                    @RequestParam(required = false) String note,
                                    @RequestParam("month") Integer month,
                                    @RequestParam("year") Integer year,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        budgetPlanningService.saveCategoryLimit(currentUser, categoryId, month, year, limitAmount, note);
        redirectAttributes.addFlashAttribute("successMessage", "Ліміт для категорії збережено!");
        return "redirect:/budget?month=" + month + "&year=" + year;
    }

    // ==================== ДЕНЬ ====================

    @GetMapping("/day/{date}")
    public String viewDay(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          Authentication authentication,
                          Model model) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        DayBudget dayBudget = budgetService.getOrCreateDayBudget(date, currentUser);
        model.addAttribute("dayBudget", dayBudget);
        model.addAttribute("expenseCategories", categoryService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", categoryService.getIncomeCategories(currentUser));
        return "day-detail";
    }

    @PostMapping("/day/{date}/expense")
    public String addExpense(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @Valid @ModelAttribute TransactionRequest request,
                             BindingResult bindingResult,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        return handleDayTransaction(date, request, bindingResult, authentication, redirectAttributes, TransactionType.EXPENSE);
    }

    @PostMapping("/day/{date}/income")
    public String addIncome(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @Valid @ModelAttribute TransactionRequest request,
                            BindingResult bindingResult,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        return handleDayTransaction(date, request, bindingResult, authentication, redirectAttributes, TransactionType.INCOME);
    }

    @PostMapping("/quick-expense")
    public String quickAddExpense(@Valid @ModelAttribute TransactionRequest request,
                                  BindingResult bindingResult,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes,
                                  HttpServletRequest httpServletRequest) {
        return handleQuickTransaction(request, bindingResult, authentication, redirectAttributes, httpServletRequest, TransactionType.EXPENSE);
    }

    @PostMapping("/quick-income")
    public String quickAddIncome(@Valid @ModelAttribute TransactionRequest request,
                                 BindingResult bindingResult,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest httpServletRequest) {
        return handleQuickTransaction(request, bindingResult, authentication, redirectAttributes, httpServletRequest, TransactionType.INCOME);
    }

    // ==================== ХЕЛПЕРИ ДЛЯ ТРАНЗАКЦІЙ ====================

    /**
     * Спільна логіка для /day/{date}/expense та /day/{date}/income.
     * Після успіху/помилки повертаємось на сторінку дня.
     */
    private String handleDayTransaction(LocalDate date,
                                        TransactionRequest request,
                                        BindingResult bindingResult,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes,
                                        TransactionType type) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", getFirstErrorMessage(bindingResult));
            return "redirect:/day/" + date;
        }

        request.setDate(date);
        budgetService.addTransaction(request, type, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", successMessageFor(type));
        return "redirect:/day/" + date;
    }

    /**
     * Спільна логіка для /quick-expense та /quick-income.
     * Після успіху/помилки повертаємось на попередню сторінку (Referer).
     */
    private String handleQuickTransaction(TransactionRequest request,
                                          BindingResult bindingResult,
                                          Authentication authentication,
                                          RedirectAttributes redirectAttributes,
                                          HttpServletRequest httpServletRequest,
                                          TransactionType type) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", getFirstErrorMessage(bindingResult));
        } else {
            budgetService.addTransaction(request, type, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", successMessageFor(type));
        }
        return redirectToReferer(httpServletRequest);
    }

    private String getFirstErrorMessage(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .map(e -> e.getDefaultMessage())
                .findFirst()
                .orElse("Невірні дані");
    }

    private String successMessageFor(TransactionType type) {
        return type == TransactionType.EXPENSE ? "Витрату додано!" : "Дохід додано!";
    }

    // ==================== ВСІ ДНІ ====================

    @GetMapping("/all-days")
    public String viewAllDays(Authentication authentication, Model model) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        model.addAttribute("allBudgets", budgetService.getAllBudgets(currentUser));
        model.addAttribute("expenseCategories", categoryService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", categoryService.getIncomeCategories(currentUser));
        return "all-days";
    }

    // ==================== ПІДСУМКИ ====================

    @GetMapping("/totals")
    public String viewTotals(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                             Authentication authentication,
                             Model model) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();

        TotalBudget totalBudget = budgetService.getTotalBudget(startDate, endDate, currentUser);
        if (totalBudget == null) {
            totalBudget = new TotalBudget();
            totalBudget.setTotalExpenses(0.0);
            totalBudget.setTotalIncomes(0.0);
            totalBudget.setBalance(0.0);
            totalBudget.setExpensesByCategory(new HashMap<>());
            totalBudget.setIncomesByCategory(new HashMap<>());
            totalBudget.setDailySummaries(new LinkedHashMap<>());
        }

        model.addAttribute("totalBudget", totalBudget);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("expenseCategories", categoryService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", categoryService.getIncomeCategories(currentUser));
        return "totals";
    }

    // ==================== КАТЕГОРІЇ ====================

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

    // ==================== ВИДАЛЕННЯ ДНЯ ====================

    @PostMapping("/day/delete/{id}")
    public String deleteDay(@PathVariable Long id,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        try {
            budgetService.deleteDay(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "День успішно видалено!");
        } catch (Exception e) {
            log.error("Error deleting day: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка при видаленні дня: " + e.getMessage());
        }
        return "redirect:/all-days";
    }

    // ==================== ДУБЛЮВАННЯ ====================

    @PostMapping("/expense/duplicate/{id}")
    public String duplicateExpense(@PathVariable Long id,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        LocalDate date = budgetService.duplicateExpense(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату дубльовано!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/income/duplicate/{id}")
    public String duplicateIncome(@PathVariable Long id,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        LocalDate date = budgetService.duplicateIncome(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід дубльовано!");
        return "redirect:/day/" + date;
    }

    // ==================== РЕДАГУВАННЯ ====================

    @PostMapping("/expense/delete/{id}")
    public String deleteExpense(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        LocalDate date = budgetService.getDateByExpense(id, currentUser);
        budgetService.deleteExpense(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату видалено!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/income/delete/{id}")
    public String deleteIncome(@PathVariable Long id,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        LocalDate date = budgetService.getDateByIncome(id, currentUser);
        budgetService.deleteIncome(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід видалено!");
        return "redirect:/day/" + date;
    }

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
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        LocalTime time = (timeStr != null && !timeStr.isEmpty()) ? LocalTime.parse(timeStr) : null;
        budgetService.updateExpense(id, description, amount, categoryId, quantity, details,
                shareWithFamily, time, buyer, seller, currentUser);
        LocalDate date = budgetService.getDateByExpense(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату оновлено!");
        return "redirect:/day/" + date;
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
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";
        LocalTime time = (timeStr != null && !timeStr.isEmpty()) ? LocalTime.parse(timeStr) : null;
        budgetService.updateIncome(id, description, amount, categoryId, quantity, details,
                shareWithFamily, time, buyer, seller, currentUser);
        LocalDate date = budgetService.getDateByIncome(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід оновлено!");
        return "redirect:/day/" + date;
    }

    // ==================== AJAX ====================

    @GetMapping("/categories/expense")
    @ResponseBody
    public List<Category> getExpenseCategoriesJson(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        return currentUser != null ? categoryService.getExpenseCategories(currentUser) : List.of();
    }

    @GetMapping("/categories/income")
    @ResponseBody
    public List<Category> getIncomeCategoriesJson(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        return currentUser != null ? categoryService.getIncomeCategories(currentUser) : List.of();
    }

    // ==================== ДОПОМІЖНІ МЕТОДИ ====================

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomOAuth2User) {
            return ((CustomOAuth2User) principal).getUser();
        } else if (principal instanceof User) {
            return (User) principal;
        }
        return null;
    }

    private void updateSecurityContext(Authentication authentication, User updatedUser) {
        Authentication newAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                updatedUser,
                authentication.getCredentials(),
                authentication.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }

    private String redirectToReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            return "redirect:" + referer;
        }
        return "redirect:/";
    }
}