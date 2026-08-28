package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.lazycent.model.*;
import org.lazycent.model.dto.BudgetDashboardDto;
import org.lazycent.model.dto.TransactionRequest;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.BudgetPlanningService;
import org.lazycent.service.BudgetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class BudgetController {

    private static final Logger log = LoggerFactory.getLogger(BudgetController.class);

    private final BudgetService budgetService;
    private final BudgetPlanningService budgetPlanningService;
    private final UserRepository userRepository; // ✅ Додано репозиторій користувачів

    // ==================== ДУБЛЮВАННЯ ====================

    @PostMapping("/expense/duplicate/{id}")
    public String duplicateExpense(@PathVariable Long id,
                                   @AuthenticationPrincipal User currentUser,
                                   RedirectAttributes redirectAttributes) {
        log.info("POST /expense/duplicate/{}", id);
        LocalDate date = budgetService.duplicateExpense(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату дубльовано!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/income/duplicate/{id}")
    public String duplicateIncome(@PathVariable Long id,
                                  @AuthenticationPrincipal User currentUser,
                                  RedirectAttributes redirectAttributes) {
        log.info("POST /income/duplicate/{}", id);
        LocalDate date = budgetService.duplicateIncome(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід дубльовано!");
        return "redirect:/day/" + date;
    }

    // ==================== ГОЛОВНА ====================

    @GetMapping("/")
    public String index(@AuthenticationPrincipal User currentUser, Model model) {
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories(currentUser));
        return "index";
    }

    // ==================== БЮДЖЕТ (НОВИЙ ДАШБОРД) ====================

    @GetMapping("/budget")
    public String viewBudget(
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            Authentication authentication,
            Model model) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        // Якщо параметри не передані — беремо поточний місяць
        if (month == null) month = LocalDate.now().getMonthValue();
        if (year == null) year = LocalDate.now().getYear();

        BudgetDashboardDto dashboard = budgetPlanningService.getDashboard(user, month, year);

        model.addAttribute("dashboard", dashboard);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories(user));

        return "budget";
    }

    // ==================== ЗБЕРЕЖЕННЯ ЗАГАЛЬНОГО БЮДЖЕТУ ====================

    @PostMapping("/budget/plan")
    public String saveBudgetPlan(@RequestParam Double totalBudget,
                                 @AuthenticationPrincipal User currentUser,
                                 RedirectAttributes redirectAttributes) {
        int month = budgetPlanningService.getCurrentMonth();
        int year = budgetPlanningService.getCurrentYear();
        budgetPlanningService.saveOrUpdateBudgetPlan(currentUser, month, year, totalBudget);
        redirectAttributes.addFlashAttribute("successMessage", "Бюджет збережено!");
        return "redirect:/budget";
    }

    // ==================== ЗБЕРЕЖЕННЯ ЛІМІТУ КАТЕГОРІЇ ====================

    @PostMapping("/budget/category-limit")
    public String saveCategoryLimit(@RequestParam Long categoryId,
                                    @RequestParam Double limitAmount,
                                    @RequestParam(required = false) String note,
                                    @AuthenticationPrincipal User currentUser,
                                    RedirectAttributes redirectAttributes) {
        int month = budgetPlanningService.getCurrentMonth();
        int year = budgetPlanningService.getCurrentYear();
        budgetPlanningService.saveCategoryLimit(currentUser, categoryId, month, year, limitAmount, note);
        redirectAttributes.addFlashAttribute("successMessage", "Ліміт для категорії збережено!");
        return "redirect:/budget";
    }

    // ==================== ДЕНЬ ====================

    @GetMapping("/day/{date}")
    public String viewDay(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @AuthenticationPrincipal User currentUser,
                          Model model) {
        DayBudget dayBudget = budgetService.getOrCreateDayBudget(date, currentUser);
        model.addAttribute("dayBudget", dayBudget);
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories(currentUser));
        return "day-detail";
    }

    // ==================== ВСІ ДНІ ====================

    @GetMapping("/all-days")
    public String viewAllDays(@AuthenticationPrincipal User currentUser, Model model) {
        model.addAttribute("allBudgets", budgetService.getAllBudgets(currentUser));
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories(currentUser));
        return "all-days";
    }

    // ==================== ПІДСУМКИ ====================

    @GetMapping("/totals")
    public String viewTotals(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                             @AuthenticationPrincipal User currentUser,
                             Model model) {
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

    // ==================== НАЛАШТУВАННЯ ====================

    @GetMapping("/settings")
    public String settings(@AuthenticationPrincipal User currentUser, Model model) {
        model.addAttribute("categories", budgetService.getAllCategories(currentUser));
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories(currentUser));
        return "settings";
    }

    // ==================== ДОДАВАННЯ ТРАНЗАКЦІЙ ====================

    @PostMapping("/day/{date}/expense")
    public String addExpense(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @Valid @ModelAttribute TransactionRequest request,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal User currentUser,
                             RedirectAttributes redirectAttributes) {
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
                            @AuthenticationPrincipal User currentUser,
                            RedirectAttributes redirectAttributes) {
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
                                  @AuthenticationPrincipal User currentUser,
                                  RedirectAttributes redirectAttributes,
                                  HttpServletRequest httpServletRequest) {
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst()
                    .orElse("Невірні дані");
            redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            String referer = httpServletRequest.getHeader("Referer");
            return "redirect:" + (referer != null && !referer.isEmpty() ? referer : "/");
        }
        budgetService.addTransaction(request, TransactionType.EXPENSE, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату додано!");
        String referer = httpServletRequest.getHeader("Referer");
        return "redirect:" + (referer != null && !referer.isEmpty() ? referer : "/budget");
    }

    @PostMapping("/quick-income")
    public String quickAddIncome(@Valid @ModelAttribute TransactionRequest request,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal User currentUser,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest httpServletRequest) {
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst()
                    .orElse("Невірні дані");
            redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            String referer = httpServletRequest.getHeader("Referer");
            return "redirect:" + (referer != null && !referer.isEmpty() ? referer : "/");
        }
        budgetService.addTransaction(request, TransactionType.INCOME, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід додано!");
        String referer = httpServletRequest.getHeader("Referer");
        return "redirect:" + (referer != null && !referer.isEmpty() ? referer : "/budget");
    }

    // ==================== РЕДАГУВАННЯ / ВИДАЛЕННЯ ====================

    @PostMapping("/expense/delete/{id}")
    public String deleteExpense(@PathVariable Long id,
                                @AuthenticationPrincipal User currentUser,
                                RedirectAttributes redirectAttributes) {
        LocalDate date = budgetService.getDateByExpense(id, currentUser);
        budgetService.deleteExpense(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату видалено!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/income/delete/{id}")
    public String deleteIncome(@PathVariable Long id,
                               @AuthenticationPrincipal User currentUser,
                               RedirectAttributes redirectAttributes) {
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
                              @AuthenticationPrincipal User currentUser,
                              RedirectAttributes redirectAttributes) {
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
                             @AuthenticationPrincipal User currentUser,
                             RedirectAttributes redirectAttributes) {
        LocalTime time = (timeStr != null && !timeStr.isEmpty()) ? LocalTime.parse(timeStr) : null;
        budgetService.updateIncome(id, description, amount, categoryId, quantity, details,
                shareWithFamily, time, buyer, seller, currentUser);
        LocalDate date = budgetService.getDateByIncome(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід оновлено!");
        return "redirect:/day/" + date;
    }

    // ==================== КАТЕГОРІЇ ====================

    @PostMapping("/categories/add")
    public String addCategory(@RequestParam String name,
                              @RequestParam String type,
                              @AuthenticationPrincipal User currentUser,
                              RedirectAttributes redirectAttributes) {
        try {
            budgetService.addCategory(name, type, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Категорію додано!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable Long id,
                                 @AuthenticationPrincipal User currentUser,
                                 RedirectAttributes redirectAttributes) {
        try {
            budgetService.deleteCategory(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Категорію видалено!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/categories/update/{id}")
    public String updateCategory(@PathVariable Long id,
                                 @RequestParam String name,
                                 @AuthenticationPrincipal User currentUser,
                                 RedirectAttributes redirectAttributes) {
        try {
            budgetService.updateCategory(id, name, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Категорію оновлено!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/settings";
    }

    // ==================== ЕКСПОРТ ВСІХ ДНІВ В EXCEL ====================

    @GetMapping("/export/all-days")
    public ResponseEntity<byte[]> exportAllDaysToExcel(@AuthenticationPrincipal User currentUser) throws Exception {
        List<DayBudget> allBudgets = budgetService.getAllBudgets(currentUser);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Всі дні");
            // ... (ваш код експорту)
            // Для економії місця я не дублюю, але він має бути присутнім
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            byte[] bytes = out.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            String filename = "all_days_" + LocalDate.now().format(dateFormatter) + ".xlsx";
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(bytes.length);
            return ResponseEntity.ok().headers(headers).body(bytes);
        }
    }

    @GetMapping("/export/day/{date}")
    public ResponseEntity<byte[]> exportDayToExcel(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                   @AuthenticationPrincipal User currentUser) throws Exception {
        DayBudget dayBudget = budgetService.getOrCreateDayBudget(date, currentUser);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        // ... (експорт дня)
        return null;
    }

    @GetMapping("/export/totals")
    public ResponseEntity<byte[]> exportTotalsToExcel(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                      @AuthenticationPrincipal User currentUser) throws Exception {
        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();
        TotalBudget totalBudget = budgetService.getTotalBudget(startDate, endDate, currentUser);
        // ... (експорт підсумків)
        return null;
    }

    // ==================== AJAX ====================

    @GetMapping("/categories/expense")
    @ResponseBody
    public List<Category> getExpenseCategoriesJson(@AuthenticationPrincipal User currentUser) {
        return budgetService.getExpenseCategories(currentUser);
    }

    @GetMapping("/categories/income")
    @ResponseBody
    public List<Category> getIncomeCategoriesJson(@AuthenticationPrincipal User currentUser) {
        return budgetService.getIncomeCategories(currentUser);
    }

    // ==================== ТЕСТ ====================

    @GetMapping("/init-demo-test")
    @ResponseBody
    public String initDemoTest() {
        return "Demo data is handled by DataLoader on startup.";
    }

    // ==================== ВИДАЛЕННЯ ДНЯ ====================

    @PostMapping("/day/delete/{id}")
    public String deleteDay(@PathVariable Long id,
                            @AuthenticationPrincipal User currentUser,
                            RedirectAttributes redirectAttributes) {
        try {
            budgetService.deleteDay(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "День успішно видалено!");
        } catch (Exception e) {
            log.error("Error deleting day: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка при видаленні дня: " + e.getMessage());
        }
        return "redirect:/all-days";
    }
}