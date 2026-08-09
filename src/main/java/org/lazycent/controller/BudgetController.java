package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.lazycent.model.*;
import org.lazycent.model.dto.TransactionRequest;
import org.lazycent.service.BudgetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    // ==================== ДУБЛЮВАННЯ ТРАНЗАКЦІЙ ====================

    @PostMapping("/transaction/duplicate/{id}")
    public String duplicateTransaction(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("POST /transaction/duplicate/{} - Request to duplicate transaction", id);
        LocalDate date = budgetService.duplicateTransaction(id);
        redirectAttributes.addFlashAttribute("successMessage", "Транзакцію дубльовано!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/expense/duplicate/{id}")
    public String duplicateExpense(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return duplicateTransaction(id, redirectAttributes);
    }

    @PostMapping("/income/duplicate/{id}")
    public String duplicateIncome(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return duplicateTransaction(id, redirectAttributes);
    }

    // ==================== ГОЛОВНА ====================

    @GetMapping("/")
    public String index(Model model) {
        log.info("GET / - Accessing index page");
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
        log.info("GET /budget - startDate={}, endDate={}", startDate, endDate);
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
            totalBudget.setDailySummaries(new LinkedHashMap<>());
        }

        model.addAttribute("totalBudget", totalBudget);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
        return "budget";
    }

    // ==================== ДЕНЬ ====================

    @GetMapping("/day/{date}")
    public String viewDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        log.info("GET /day/{} - Viewing day page", date);
        DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
        model.addAttribute("dayBudget", dayBudget);
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
        return "day-detail";
    }

    // ==================== ВСІ ДНІ ====================

    @GetMapping("/all-days")
    public String viewAllDays(Model model) {
        List<DayBudget> allBudgets = budgetService.getAllBudgets();
        model.addAttribute("allBudgets", allBudgets);
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
        return "all-days";
    }

    // ==================== ПІДСУМКИ ====================

    @GetMapping("/totals")
    public String viewTotals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {
        log.info("GET /totals - startDate={}, endDate={}", startDate, endDate);
        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();

        TotalBudget totalBudget = budgetService.getTotalBudget(startDate, endDate);
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
        return "totals";
    }

    // ==================== НАЛАШТУВАННЯ ====================

    @GetMapping("/settings")
    public String settings(Model model) {
        log.info("GET /settings - Viewing settings page");
        model.addAttribute("categories", budgetService.getAllCategories());
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
        return "settings";
    }

    // ==================== ДОДАВАННЯ ТРАНЗАКЦІЙ ====================

    @PostMapping("/day/{date}/expense")
    public String addExpense(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @ModelAttribute TransactionRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        log.info("POST /day/{}/expense - Adding expense: {}", date, request);

        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .collect(java.util.stream.Collectors.joining("; "));
            log.warn("Validation errors for expense: {}", errorMessage);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилки валідації: " + errorMessage);
            redirectAttributes.addFlashAttribute("formData", request);
            return "redirect:/day/" + date;
        }

        request.setDate(date);
        budgetService.addTransaction(request, TransactionType.EXPENSE);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату додано!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/day/{date}/income")
    public String addIncome(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @ModelAttribute TransactionRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        log.info("POST /day/{}/income - Adding income: {}", date, request);

        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .collect(java.util.stream.Collectors.joining("; "));
            log.warn("Validation errors for income: {}", errorMessage);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилки валідації: " + errorMessage);
            redirectAttributes.addFlashAttribute("formData", request);
            return "redirect:/day/" + date;
        }

        request.setDate(date);
        budgetService.addTransaction(request, TransactionType.INCOME);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід додано!");
        return "redirect:/day/" + date;
    }

    private boolean isValidRedirect(String url) {
        if (url == null || url.isEmpty()) return false;
        if (!url.startsWith("/") || url.startsWith("//")) return false;
        return !url.contains("..");
    }

    @PostMapping("/quick-expense")
    public String quickAddExpense(
            @Valid @ModelAttribute TransactionRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            HttpServletRequest httpServletRequest) {

        log.info("POST /quick-expense - Quick add expense: {}", request);

        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .collect(java.util.stream.Collectors.joining("; "));
            log.warn("Validation errors for quick expense: {}", errorMessage);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилки валідації: " + errorMessage);
            String referer = httpServletRequest.getHeader("Referer");
            if (isValidRedirect(referer)) {
                return "redirect:" + referer;
            }
            return "redirect:/";
        }

        budgetService.addTransaction(request, TransactionType.EXPENSE);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату додано!");
        String referer = httpServletRequest.getHeader("Referer");
        if (isValidRedirect(referer)) {
            return "redirect:" + referer;
        }
        return "redirect:/budget";
    }

    @PostMapping("/quick-income")
    public String quickAddIncome(
            @Valid @ModelAttribute TransactionRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            HttpServletRequest httpServletRequest) {

        log.info("POST /quick-income - Quick add income: {}", request);

        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .collect(java.util.stream.Collectors.joining("; "));
            log.warn("Validation errors for quick income: {}", errorMessage);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилки валідації: " + errorMessage);
            String referer = httpServletRequest.getHeader("Referer");
            if (isValidRedirect(referer)) {
                return "redirect:" + referer;
            }
            return "redirect:/";
        }

        budgetService.addTransaction(request, TransactionType.INCOME);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід додано!");
        String referer = httpServletRequest.getHeader("Referer");
        if (isValidRedirect(referer)) {
            return "redirect:" + referer;
        }
        return "redirect:/budget";
    }

    // ==================== ВИДАЛЕННЯ ТРАНЗАКЦІЙ ====================

    @PostMapping("/transaction/delete/{id}")
    public String deleteTransaction(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("POST /transaction/delete/{} - Deleting transaction", id);
        LocalDate date = budgetService.getDateByTransaction(id);
        budgetService.deleteTransaction(id);
        redirectAttributes.addFlashAttribute("successMessage", "Транзакцію видалено!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/expense/delete/{id}")
    public String deleteExpense(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return deleteTransaction(id, redirectAttributes);
    }

    @PostMapping("/income/delete/{id}")
    public String deleteIncome(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return deleteTransaction(id, redirectAttributes);
    }

    // ==================== РЕДАГУВАННЯ ====================

    @PostMapping("/transaction/edit/{id}")
    public String editTransaction(
            @PathVariable Long id,
            @RequestParam Long categoryId,
            @RequestParam String description,
            @RequestParam Double amount,
            @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
            @RequestParam(value = "details", required = false) String details,
            @RequestParam(value = "shareWithFamily", required = false, defaultValue = "false") Boolean shareWithFamily,
            @RequestParam(value = "time", required = false) String timeStr,
            @RequestParam(value = "buyer", required = false) String buyer,
            @RequestParam(value = "seller", required = false) String seller,
            RedirectAttributes redirectAttributes) {

        log.info("POST /transaction/edit/{} - Editing transaction", id);
        LocalTime time = null;
        if (timeStr != null && !timeStr.isEmpty()) {
            time = LocalTime.parse(timeStr);
        }
        budgetService.updateTransaction(id, description, amount, categoryId, quantity, details,
                shareWithFamily, time, buyer, seller);
        LocalDate date = budgetService.getDateByTransaction(id);
        redirectAttributes.addFlashAttribute("successMessage", "Транзакцію оновлено!");
        return "redirect:/day/" + date;
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
            @RequestParam(value = "seller", required = false) String seller,
            RedirectAttributes redirectAttributes) {
        return editTransaction(id, categoryId, description, amount, quantity, details,
                shareWithFamily, timeStr, buyer, seller, redirectAttributes);
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
            @RequestParam(value = "seller", required = false) String seller,
            RedirectAttributes redirectAttributes) {
        return editTransaction(id, categoryId, description, amount, quantity, details,
                shareWithFamily, timeStr, buyer, seller, redirectAttributes);
    }

    // ==================== КАТЕГОРІЇ ====================

    @PostMapping("/categories/add")
    public String addCategory(@RequestParam String name, @RequestParam String type, RedirectAttributes redirectAttributes) {
        log.info("POST /categories/add - Adding category: name='{}', type='{}'", name, type);
        budgetService.addCategory(name, type);
        redirectAttributes.addFlashAttribute("successMessage", "Категорію додано!");
        return "redirect:/settings";
    }

    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("POST /categories/delete/{} - Deleting category", id);
        budgetService.deleteCategory(id);
        redirectAttributes.addFlashAttribute("successMessage", "Категорію видалено!");
        return "redirect:/settings";
    }

    @PostMapping("/categories/update/{id}")
    public String updateCategory(@PathVariable Long id, @RequestParam String name, RedirectAttributes redirectAttributes) {
        log.info("POST /categories/update/{} - Updating category to name='{}'", id, name);
        budgetService.updateCategory(id, name);
        redirectAttributes.addFlashAttribute("successMessage", "Категорію оновлено!");
        return "redirect:/settings";
    }

    // ==================== ЕКСПОРТ В EXCEL ====================

    @GetMapping("/export/day/{date}")
    public ResponseEntity<byte[]> exportDayToExcel(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) throws Exception {

        log.info("GET /export/day/{} - Exporting day to Excel", date);
        DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("День " + dateFormatter.format(date));

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"№", "Тип", "Час", "Найменування", "Кількість", "Сума", "Покупець", "Продавець", "Деталі", "Категорія"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            int rowNum = 1;
            int index = 1;

            for (Transaction t : dayBudget.getTransactions()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(index++);
                row.createCell(1).setCellValue(t.isExpense() ? "Витрата" : "Дохід");
                row.createCell(2).setCellValue(t.getTime() != null ? t.getTime().toString() : "");
                row.createCell(3).setCellValue(t.getDescription() != null ? t.getDescription() : "");
                row.createCell(4).setCellValue(t.getQuantity() != null ? t.getQuantity() : 1);
                row.createCell(5).setCellValue(t.getAmount() != null ? t.getAmount() : 0);
                row.createCell(6).setCellValue(t.getBuyer() != null ? t.getBuyer() : "");
                row.createCell(7).setCellValue(t.getSeller() != null ? t.getSeller() : "");
                row.createCell(8).setCellValue(t.getDetails() != null ? t.getDetails() : "");
                row.createCell(9).setCellValue(t.getCategory() != null ? t.getCategory().getName() : "");
            }

            Row summaryRow = sheet.createRow(rowNum);
            CellStyle boldStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldStyle.setFont(boldFont);
            summaryRow.createCell(0).setCellValue("Підсумки:");
            summaryRow.createCell(5).setCellValue(dayBudget.getTotalExpenses());

            Row incomeSummaryRow = sheet.createRow(rowNum + 1);
            incomeSummaryRow.createCell(0).setCellValue("Доходи:");
            incomeSummaryRow.createCell(5).setCellValue(dayBudget.getTotalIncomes());

            Row balanceRow = sheet.createRow(rowNum + 2);
            balanceRow.createCell(0).setCellValue("Баланс:");
            balanceRow.createCell(5).setCellValue(dayBudget.getRemainingBalance());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            byte[] bytes = out.toByteArray();

            HttpHeaders headers2 = new HttpHeaders();
            headers2.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers2.setContentDispositionFormData("attachment", "day_" + dateFormatter.format(date) + ".xlsx");
            headers2.setContentLength(bytes.length);

            return ResponseEntity.ok().headers(headers2).body(bytes);
        }
    }

    // ==================== AJAX ДЛЯ КАТЕГОРІЙ ====================

    @GetMapping("/categories/expense")
    @ResponseBody
    public List<Category> getExpenseCategoriesJson() {
        log.debug("GET /categories/expense - AJAX request for expense categories");
        return budgetService.getExpenseCategories();
    }

    @GetMapping("/categories/income")
    @ResponseBody
    public List<Category> getIncomeCategoriesJson() {
        log.debug("GET /categories/income - AJAX request for income categories");
        return budgetService.getIncomeCategories();
    }

    // ==================== ТЕСТ ====================

    @GetMapping("/init-demo-test")
    @ResponseBody
    public String initDemoTest() {
        log.info("GET /init-demo-test - Demo test endpoint called");
        return "Demo data is handled by DataLoader on startup.";
    }

    @PostMapping("/day/delete/{id}")
    public String deleteDay(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("POST /day/delete/{} - Deleting day", id);
        try {
            budgetService.deleteDay(id);
            redirectAttributes.addFlashAttribute("successMessage", "День успішно видалено!");
        } catch (Exception e) {
            log.error("Error deleting day: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Помилка при видаленні дня: " + e.getMessage());
        }
        return "redirect:/all-days";
    }
}