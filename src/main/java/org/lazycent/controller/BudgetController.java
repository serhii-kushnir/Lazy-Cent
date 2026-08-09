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

    // ==================== ДУБЛЮВАННЯ ====================

    @PostMapping("/expense/duplicate/{id}")
    public String duplicateExpense(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("POST /expense/duplicate/{}", id);
        LocalDate date = budgetService.duplicateExpense(id);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату дубльовано!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/income/duplicate/{id}")
    public String duplicateIncome(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("POST /income/duplicate/{}", id);
        LocalDate date = budgetService.duplicateIncome(id);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід дубльовано!");
        return "redirect:/day/" + date;
    }

    // ==================== ГОЛОВНА ====================

    @GetMapping("/")
    public String index(Model model) {
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
    public String viewDay(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
        model.addAttribute("dayBudget", dayBudget);
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
        return "day-detail";
    }

    // ==================== ВСІ ДНІ ====================

    @GetMapping("/all-days")
    public String viewAllDays(Model model) {
        model.addAttribute("allBudgets", budgetService.getAllBudgets());
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
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst()
                    .orElse("Невірні дані");
            redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
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
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst()
                    .orElse("Невірні дані");
            redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            return "redirect:/day/" + date;
        }
        request.setDate(date);
        budgetService.addTransaction(request, TransactionType.INCOME);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід додано!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/quick-expense")
    public String quickAddExpense(
            @Valid @ModelAttribute TransactionRequest request,
            BindingResult bindingResult,
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
        budgetService.addTransaction(request, TransactionType.EXPENSE);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату додано!");
        String referer = httpServletRequest.getHeader("Referer");
        return "redirect:" + (referer != null && !referer.isEmpty() ? referer : "/budget");
    }

    @PostMapping("/quick-income")
    public String quickAddIncome(
            @Valid @ModelAttribute TransactionRequest request,
            BindingResult bindingResult,
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
        budgetService.addTransaction(request, TransactionType.INCOME);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід додано!");
        String referer = httpServletRequest.getHeader("Referer");
        return "redirect:" + (referer != null && !referer.isEmpty() ? referer : "/budget");
    }

    // ==================== РЕДАГУВАННЯ / ВИДАЛЕННЯ ====================

    @PostMapping("/expense/delete/{id}")
    public String deleteExpense(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        LocalDate date = budgetService.getDateByExpense(id);
        budgetService.deleteExpense(id);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату видалено!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/income/delete/{id}")
    public String deleteIncome(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        LocalDate date = budgetService.getDateByIncome(id);
        budgetService.deleteIncome(id);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід видалено!");
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
        LocalTime time = (timeStr != null && !timeStr.isEmpty()) ? LocalTime.parse(timeStr) : null;
        budgetService.updateExpense(id, description, amount, categoryId, quantity, details, shareWithFamily, time, buyer, seller);
        LocalDate date = budgetService.getDateByExpense(id);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату оновлено!");
        return "redirect:/day/" + date;
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
        LocalTime time = (timeStr != null && !timeStr.isEmpty()) ? LocalTime.parse(timeStr) : null;
        budgetService.updateIncome(id, description, amount, categoryId, quantity, details, shareWithFamily, time, buyer, seller);
        LocalDate date = budgetService.getDateByIncome(id);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід оновлено!");
        return "redirect:/day/" + date;
    }

    // ==================== КАТЕГОРІЇ ====================

    @PostMapping("/categories/add")
    public String addCategory(@RequestParam String name, @RequestParam String type, RedirectAttributes redirectAttributes) {
        budgetService.addCategory(name, type);
        redirectAttributes.addFlashAttribute("successMessage", "Категорію додано!");
        return "redirect:/settings";
    }

    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        budgetService.deleteCategory(id);
        redirectAttributes.addFlashAttribute("successMessage", "Категорію видалено!");
        return "redirect:/settings";
    }

    @PostMapping("/categories/update/{id}")
    public String updateCategory(@PathVariable Long id, @RequestParam String name, RedirectAttributes redirectAttributes) {
        budgetService.updateCategory(id, name);
        redirectAttributes.addFlashAttribute("successMessage", "Категорію оновлено!");
        return "redirect:/settings";
    }

    // ==================== ЕКСПОРТ В EXCEL ====================

    @GetMapping("/export/day/{date}")
    public ResponseEntity<byte[]> exportDayToExcel(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) throws Exception {
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

            for (Expense exp : dayBudget.getExpenses()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(index++);
                row.createCell(1).setCellValue("Витрата");
                row.createCell(2).setCellValue(exp.getTime() != null ? exp.getTime().toString() : "");
                row.createCell(3).setCellValue(exp.getDescription() != null ? exp.getDescription() : "");
                row.createCell(4).setCellValue(exp.getQuantity() != null ? exp.getQuantity() : 1);
                row.createCell(5).setCellValue(exp.getAmount() != null ? exp.getAmount() : 0);
                row.createCell(6).setCellValue(exp.getBuyer() != null ? exp.getBuyer() : "");
                row.createCell(7).setCellValue(exp.getSeller() != null ? exp.getSeller() : "");
                row.createCell(8).setCellValue(exp.getDetails() != null ? exp.getDetails() : "");
                row.createCell(9).setCellValue(exp.getCategory() != null ? exp.getCategory().getName() : "");
            }

            for (Income inc : dayBudget.getIncomes()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(index++);
                row.createCell(1).setCellValue("Дохід");
                row.createCell(2).setCellValue(inc.getTime() != null ? inc.getTime().toString() : "");
                row.createCell(3).setCellValue(inc.getDescription() != null ? inc.getDescription() : "");
                row.createCell(4).setCellValue(inc.getQuantity() != null ? inc.getQuantity() : 1);
                row.createCell(5).setCellValue(inc.getAmount() != null ? inc.getAmount() : 0);
                row.createCell(6).setCellValue(inc.getBuyer() != null ? inc.getBuyer() : "");
                row.createCell(7).setCellValue(inc.getSeller() != null ? inc.getSeller() : "");
                row.createCell(8).setCellValue(inc.getDetails() != null ? inc.getDetails() : "");
                row.createCell(9).setCellValue(inc.getCategory() != null ? inc.getCategory().getName() : "");
            }

            Row summaryRow = sheet.createRow(rowNum);
            CellStyle boldStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldStyle.setFont(boldFont);
            Cell sumCell = summaryRow.createCell(0);
            sumCell.setCellValue("Підсумки:");
            sumCell.setCellStyle(boldStyle);
            summaryRow.createCell(5).setCellValue(dayBudget.getTotalExpenses() != null ? dayBudget.getTotalExpenses() : 0);
            summaryRow.createCell(5).setCellStyle(boldStyle);

            Row incomeSummaryRow = sheet.createRow(rowNum + 1);
            incomeSummaryRow.createCell(0).setCellValue("Доходи:");
            incomeSummaryRow.createCell(5).setCellValue(dayBudget.getTotalIncomes() != null ? dayBudget.getTotalIncomes() : 0);

            Row balanceRow = sheet.createRow(rowNum + 2);
            balanceRow.createCell(0).setCellValue("Баланс:");
            balanceRow.createCell(5).setCellValue(dayBudget.getRemainingBalance() != null ? dayBudget.getRemainingBalance() : 0);

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

    // ==================== AJAX ====================

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

    // ==================== ТЕСТ ====================

    @GetMapping("/init-demo-test")
    @ResponseBody
    public String initDemoTest() {
        return "Demo data is handled by DataLoader on startup.";
    }

    @PostMapping("/day/delete/{id}")
    public String deleteDay(@PathVariable Long id, RedirectAttributes redirectAttributes) {
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