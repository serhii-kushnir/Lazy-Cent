// BudgetController.java (повністю виправлений)
package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.lazycent.model.dto.CategoryBudgetDto;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @PostMapping("/generate-demo")
    public String generateDemo(Authentication authentication, RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            log.info("=== ГЕНЕРАЦІЯ ДЕМО-ДАННИХ ДЛЯ КОРИСТУВАЧА: {} (id={}) ===", currentUser.getUsername(), currentUser.getId());
            long beforeCount = dayBudgetRepository.countByOwner(currentUser);
            log.info("Кількість днів до генерації: {}", beforeCount);

            demoDataService.createDefaultCategories(currentUser);
            demoDataService.createRandomTransactions(currentUser, 14);

            long afterCount = dayBudgetRepository.countByOwner(currentUser);
            log.info("Кількість днів після генерації: {}", afterCount);

            if (afterCount > beforeCount) {
                redirectAttributes.addFlashAttribute("successMessage", "Демо-дані успішно додано! 🎉");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Демо-дані не були додані. Перевірте логи.");
            }
        } catch (Exception e) {
            log.error("Помилка генерації демо-даних: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка: " + e.getMessage());
        }
        return "redirect:/";
    }

    // ==================== НАЛАШТУВАННЯ ====================

    @GetMapping("/settings")
    public String settings(Authentication authentication, Model model) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("categories", budgetService.getAllCategories(currentUser));
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories(currentUser));
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories(currentUser));
        model.addAttribute("userEmail", currentUser.getEmail());
        return "settings";
    }

    @PostMapping("/settings/profile")
    public String updateProfile(@RequestParam String username,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String currentPassword,
                                @RequestParam(required = false) String newPassword,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            userService.updateProfile(currentUser, username, email, currentPassword, newPassword);
            redirectAttributes.addFlashAttribute("successMessage", "Профіль успішно оновлено!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/settings";
    }

    // ==================== БЮДЖЕТ ====================

    // BudgetController.java – оновлений метод viewBudget з передачею даних для діаграм
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

        // 👇 Створюємо Map<String, Double> для діаграм
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
        model.addAttribute("categoryExpenses", categoryExpenses); // 👈 новий атрибут

        return "budget";
    }

    // BudgetController.java – виправлені методи збереження бюджету та ліміту

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
            String referer = httpServletRequest.getHeader("Referer");
            return "redirect:" + (referer != null && !referer.isEmpty() ? referer : "/");
        }
        budgetService.addTransaction(request, TransactionType.EXPENSE, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату додано!");
        String referer = httpServletRequest.getHeader("Referer");
        return "redirect:" + (referer != null && !referer.isEmpty() ? referer : "/");
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
            String referer = httpServletRequest.getHeader("Referer");
            return "redirect:" + (referer != null && !referer.isEmpty() ? referer : "/");
        }
        budgetService.addTransaction(request, TransactionType.INCOME, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід додано!");
        String referer = httpServletRequest.getHeader("Referer");
        return "redirect:" + (referer != null && !referer.isEmpty() ? referer : "/");
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

    // ==================== ЕКСПОРТ ====================

    @GetMapping("/export/all-days")
    public ResponseEntity<byte[]> exportAllDaysToExcel(Authentication authentication) throws Exception {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        List<DayBudget> allBudgets = budgetService.getAllBudgets(currentUser);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Всі дні");
            // ... логіка експорту ...
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
                                                   Authentication authentication) throws Exception {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        DayBudget dayBudget = budgetService.getOrCreateDayBudget(date, currentUser);
        // ... логіка експорту ...
        return null;
    }

    @GetMapping("/export/totals")
    public ResponseEntity<byte[]> exportTotalsToExcel(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                      Authentication authentication) throws Exception {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();
        TotalBudget totalBudget = budgetService.getTotalBudget(startDate, endDate, currentUser);
        // ... логіка експорту ...
        return null;
    }

    // ==================== КАТЕГОРІЇ ====================

    @PostMapping("/categories/add")
    public String addCategory(@RequestParam String name,
                              @RequestParam String type,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
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
        return "redirect:/settings";
    }

    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable Long id,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
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
        return "redirect:/settings";
    }

    @PostMapping("/categories/update/{id}")
    public String updateCategory(@PathVariable Long id,
                                 @RequestParam String name,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
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
        return "redirect:/settings";
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

    // BudgetController.java – додати метод для експорту бюджету в Excel

    @GetMapping("/export/budget")
    public ResponseEntity<byte[]> exportBudgetToExcel(
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            Authentication authentication) throws Exception {

        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }

        if (month == null) month = LocalDate.now().getMonthValue();
        if (year == null) year = LocalDate.now().getYear();

        BudgetDashboardDto dashboard = budgetPlanningService.getDashboard(currentUser, month, year);

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Бюджет");

            // Стилі
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setAlignment(HorizontalAlignment.RIGHT);
            numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            // Заголовок
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Бюджет на " + month + "/" + year);
            titleCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            // Загальна інформація
            int rowNum = 2;
            Row infoRow = sheet.createRow(rowNum++);
            infoRow.createCell(0).setCellValue("Загальний бюджет:");
            infoRow.createCell(1).setCellValue(dashboard.getTotalBudget());

            infoRow = sheet.createRow(rowNum++);
            infoRow.createCell(0).setCellValue("Витрачено:");
            infoRow.createCell(1).setCellValue(dashboard.getTotalExpenses());

            infoRow = sheet.createRow(rowNum++);
            infoRow.createCell(0).setCellValue("Залишок:");
            infoRow.createCell(1).setCellValue(dashboard.getRemainingBudget());

            infoRow = sheet.createRow(rowNum++);
            infoRow.createCell(0).setCellValue("Прогноз:");
            infoRow.createCell(1).setCellValue(dashboard.getProjectedBalance());

            rowNum++; // порожній рядок

            // Таблиця категорій
            Row headerRow = sheet.createRow(rowNum++);
            headerRow.createCell(0).setCellValue("Категорія");
            headerRow.createCell(1).setCellValue("Витрачено");
            headerRow.createCell(2).setCellValue("Ліміт");
            headerRow.createCell(3).setCellValue("Використано %");
            headerRow.createCell(4).setCellValue("Статус");

            for (int i = 0; i <= 4; i++) {
                headerRow.getCell(i).setCellStyle(headerStyle);
            }

            for (CategoryBudgetDto catDto : dashboard.getCategories()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(catDto.getCategory().getName());
                row.createCell(1).setCellValue(catDto.getSpent());
                row.createCell(1).setCellStyle(numberStyle);
                row.createCell(2).setCellValue(catDto.getLimit());
                row.createCell(2).setCellStyle(numberStyle);
                row.createCell(3).setCellValue(catDto.getPercentUsed());
                row.createCell(4).setCellValue(catDto.getStatus());
            }

            // Автовиправлення ширини колонок
            for (int i = 0; i <= 4; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            byte[] bytes = out.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            String filename = "budget_" + month + "_" + year + "_" + LocalDate.now().format(dateFormatter) + ".xlsx";
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(bytes.length);

            return ResponseEntity.ok().headers(headers).body(bytes);
        }
    }
}