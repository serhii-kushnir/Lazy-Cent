package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.*;
import org.lazycent.model.dto.BudgetDashboardDto;
import org.lazycent.model.dto.TransactionRequest;
import org.lazycent.repository.CategoryRepository;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.BudgetPlanningService;
import org.lazycent.service.BudgetService;
import org.lazycent.service.DemoDataService;
import org.lazycent.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BudgetController {

    private final BudgetService budgetService;
    private final BudgetPlanningService budgetPlanningService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final DemoDataService demoDataService;
    private final CategoryRepository categoryRepository;
    private final DayBudgetRepository dayBudgetRepository;

    // ==================== ГОЛОВНА ====================

    @GetMapping("/")
    public String index(Authentication authentication, Model model) {
        User currentUser = getCurrentUser(authentication);
        boolean showDemoBanner = false;

        if (currentUser != null) {
            long dayBudgetCount = dayBudgetRepository.countByOwner(currentUser);
            showDemoBanner = (dayBudgetCount == 0);

            model.addAttribute("today", LocalDate.now());
            model.addAttribute("expenseCategories", budgetService.getExpenseCategories(currentUser));
            model.addAttribute("incomeCategories", budgetService.getIncomeCategories(currentUser));

            TotalBudget todaySummary = budgetService.getTodaySummary(currentUser);
            TotalBudget weekSummary = budgetService.getWeekSummary(currentUser);
            TotalBudget monthSummary = budgetService.getMonthSummary(currentUser);

            model.addAttribute("todaySummary", todaySummary != null ? todaySummary : new TotalBudget());
            model.addAttribute("weekSummary", weekSummary != null ? weekSummary : new TotalBudget());
            model.addAttribute("monthSummary", monthSummary != null ? monthSummary : new TotalBudget());
        }

        model.addAttribute("showDemoBanner", showDemoBanner);
        return "index";
    }

    // ==================== НАЛАШТУВАННЯ ====================

    @PostMapping("/settings/currency")
    public String updateCurrency(@RequestParam String currency,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            // 1. Зберігаємо валюту в БД (точковий UPDATE, не торкається колекцій)
            userService.updateCurrency(currentUser, currency);

            // 2. Оновлюємо SecurityContext — завантажуємо свіжого користувача з БД
            User updatedUser = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

            Authentication newAuth = new UsernamePasswordAuthenticationToken(
                    updatedUser,
                    authentication.getCredentials(),
                    authentication.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(newAuth);

            log.info("Валюта оновлена на {} для користувача {}", currency, updatedUser.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Валюту змінено на " + currency);

        } catch (Exception e) {
            log.error("Помилка зміни валюти: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка: " + e.getMessage());
        }

        return redirectToReferer(request, "/");
    }

    @PostMapping("/settings/language")
    public String updateLanguage(@RequestParam String lang,
                                 Authentication authentication,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            // 1. Зберігаємо мову в БД (точковий UPDATE)
            userService.updateLanguage(currentUser, lang);

            // 2. Оновлюємо локаль у сесії
            LocaleResolver localeResolver = RequestContextUtils.getLocaleResolver(request);
            if (localeResolver != null) {
                localeResolver.setLocale(request, response, Locale.forLanguageTag(lang));
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    "uk".equals(lang) ? "Мову змінено на українську" : "Language changed to English");

        } catch (Exception e) {
            log.error("Помилка зміни мови: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка: " + e.getMessage());
        }

        return redirectToReferer(request, "/");
    }

    @PostMapping("/settings/profile")
    public String updateProfile(@RequestParam String username,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String currentPassword,
                                @RequestParam(required = false) String newPassword,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes,
                                HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            userService.updateProfile(currentUser, username, email, currentPassword, newPassword);

            // Оновлюємо SecurityContext після зміни профілю
            User updatedUser = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

            Authentication newAuth = new UsernamePasswordAuthenticationToken(
                    updatedUser,
                    authentication.getCredentials(),
                    authentication.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(newAuth);

            redirectAttributes.addFlashAttribute("successMessage", "Профіль успішно оновлено!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return redirectToReferer(request, "/");
    }

    @PostMapping("/settings/clear-data")
    public String clearData(Authentication authentication,
                            RedirectAttributes redirectAttributes,
                            HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            budgetService.clearAllUserData(currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Всі дані успішно видалено!");
        } catch (Exception e) {
            log.error("Помилка при очищенні даних: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка при очищенні даних: " + e.getMessage());
        }

        return redirectToReferer(request, "/");
    }

    @PostMapping("/settings/delete-account")
    public String deleteAccount(Authentication authentication,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

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
    public String generateDemo(Authentication authentication,
                               RedirectAttributes redirectAttributes,
                               HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            demoDataService.createDefaultCategories(currentUser);
            demoDataService.createRandomTransactions(currentUser, 14);
            redirectAttributes.addFlashAttribute("successMessage", "Демо-дані успішно додано! 🎉");
        } catch (Exception e) {
            log.error("Помилка генерації демо-даних: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка: " + e.getMessage());
        }

        return redirectToReferer(request, "/");
    }

    // ==================== БЮДЖЕТ ====================

    @GetMapping("/budget")
    public String viewBudget(
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            Authentication authentication,
            Model model) {

        User user = getCurrentUser(authentication);
        if (user == null) {
            return "redirect:/login";
        }

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
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories(user));
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories(user));
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
        if (currentUser == null) {
            return "redirect:/login";
        }
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
        if (currentUser == null) {
            return "redirect:/login";
        }
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
        if (currentUser == null) {
            return "redirect:/login";
        }
        DayBudget dayBudget = budgetService.getOrCreateDayBudget(date, currentUser);
        model.addAttribute("dayBudget", dayBudget);
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories(currentUser));
        return "day-detail";
    }

    @PostMapping("/day/{date}/expense")
    public String addExpense(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @Valid @ModelAttribute TransactionRequest request,
                             BindingResult bindingResult,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst()
                    .orElse("Невірні дані");
            redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            return "redirect:/day/" + date;
        }
        request.setDate(date);
        budgetService.addTransaction(request, TransactionType.EXPENSE, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату додано!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/day/{date}/income")
    public String addIncome(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @Valid @ModelAttribute TransactionRequest request,
                            BindingResult bindingResult,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst()
                    .orElse("Невірні дані");
            redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            return "redirect:/day/" + date;
        }
        request.setDate(date);
        budgetService.addTransaction(request, TransactionType.INCOME, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід додано!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/quick-expense")
    public String quickAddExpense(@Valid @ModelAttribute TransactionRequest request,
                                  BindingResult bindingResult,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes,
                                  HttpServletRequest httpServletRequest) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst()
                    .orElse("Невірні дані");
            redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            return redirectToReferer(httpServletRequest, "/");
        }
        budgetService.addTransaction(request, TransactionType.EXPENSE, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату додано!");
        return redirectToReferer(httpServletRequest, "/");
    }

    @PostMapping("/quick-income")
    public String quickAddIncome(@Valid @ModelAttribute TransactionRequest request,
                                 BindingResult bindingResult,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest httpServletRequest) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst()
                    .orElse("Невірні дані");
            redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            return redirectToReferer(httpServletRequest, "/");
        }
        budgetService.addTransaction(request, TransactionType.INCOME, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід додано!");
        return redirectToReferer(httpServletRequest, "/");
    }

    // ==================== ВСІ ДНІ ====================

    @GetMapping("/all-days")
    public String viewAllDays(Authentication authentication, Model model) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
        model.addAttribute("allBudgets", budgetService.getAllBudgets(currentUser));
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories(currentUser));
        return "all-days";
    }

    // ==================== ПІДСУМКИ ====================

    @GetMapping("/totals")
    public String viewTotals(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                             Authentication authentication,
                             Model model) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
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
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories(currentUser));
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
        if (currentUser == null) {
            return "redirect:/login";
        }
        try {
            budgetService.addCategory(name, type, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Категорію додано!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectToReferer(request, "/");
    }

    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable Long id,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
        try {
            budgetService.deleteCategory(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Категорію видалено!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectToReferer(request, "/");
    }

    @PostMapping("/categories/update/{id}")
    public String updateCategory(@PathVariable Long id,
                                 @RequestParam String name,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
        try {
            budgetService.updateCategory(id, name, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Категорію оновлено!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectToReferer(request, "/");
    }

    // ==================== ВИДАЛЕННЯ ДНЯ ====================

    @PostMapping("/day/delete/{id}")
    public String deleteDay(@PathVariable Long id,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
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
        if (currentUser == null) {
            return "redirect:/login";
        }
        LocalDate date = budgetService.duplicateExpense(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату дубльовано!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/income/duplicate/{id}")
    public String duplicateIncome(@PathVariable Long id,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
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
        if (currentUser == null) {
            return "redirect:/login";
        }
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
        if (currentUser == null) {
            return "redirect:/login";
        }
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
        if (currentUser == null) {
            return "redirect:/login";
        }
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
        if (currentUser == null) {
            return "redirect:/login";
        }
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
        return currentUser != null ? budgetService.getExpenseCategories(currentUser) : List.of();
    }

    @GetMapping("/categories/income")
    @ResponseBody
    public List<Category> getIncomeCategoriesJson(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        return currentUser != null ? budgetService.getIncomeCategories(currentUser) : List.of();
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

    /**
     * Повертає redirect на Referer, якщо він є і виглядає безпечно.
     * Інакше — на fallbackUrl.
     */
    private String redirectToReferer(HttpServletRequest request, String fallbackUrl) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            // Захист від open-redirect: дозволяємо тільки відносні шляхи або свій origin
            try {
                java.net.URI uri = java.net.URI.create(referer);
                String path = uri.getPath();
                if (path != null && !path.isEmpty() && uri.getHost() == null) {
                    // відносний шлях
                    return "redirect:" + referer;
                }
                if (path != null && !path.isEmpty()) {
                    // повний URL — ігноруємо хост, редіректимо на свій
                    return "redirect:" + path;
                }
            } catch (IllegalArgumentException e) {
                // некоректний URL — ігноруємо
            }
        }
        return "redirect:" + fallbackUrl;
    }
}