package org.lazycent.controller;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
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

    // ==================== ЕКСПОРТ ВСІХ ДНІВ В EXCEL ====================

    @GetMapping("/export/all-days")
    public ResponseEntity<byte[]> exportAllDaysToExcel() throws Exception {
        List<DayBudget> allBudgets = budgetService.getAllBudgets();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Всі дні");

            // Стиль для заголовків
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Стиль для комірок з датою
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setAlignment(HorizontalAlignment.CENTER);
            dateStyle.setBorderBottom(BorderStyle.THIN);
            dateStyle.setBorderTop(BorderStyle.THIN);
            dateStyle.setBorderLeft(BorderStyle.THIN);
            dateStyle.setBorderRight(BorderStyle.THIN);

            // Стиль для чисел
            CellStyle amountStyle = workbook.createCellStyle();
            amountStyle.setAlignment(HorizontalAlignment.RIGHT);
            amountStyle.setBorderBottom(BorderStyle.THIN);
            amountStyle.setBorderTop(BorderStyle.THIN);
            amountStyle.setBorderLeft(BorderStyle.THIN);
            amountStyle.setBorderRight(BorderStyle.THIN);
            amountStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            // Стиль для позитивного балансу (світло-зелений)
            CellStyle balancePositiveStyle = workbook.createCellStyle();
            balancePositiveStyle.cloneStyleFrom(amountStyle);
            balancePositiveStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            balancePositiveStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Стиль для негативного балансу (світло-червоний)
            CellStyle balanceNegativeStyle = workbook.createCellStyle();
            balanceNegativeStyle.cloneStyleFrom(amountStyle);
            balanceNegativeStyle.setFillForegroundColor(IndexedColors.PINK.getIndex());  // ЗАМІСТЬ LIGHT_RED
            balanceNegativeStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Стиль для підсумків
            CellStyle summaryStyle = workbook.createCellStyle();
            Font summaryFont = workbook.createFont();
            summaryFont.setBold(true);
            summaryStyle.setFont(summaryFont);
            summaryStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            summaryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            summaryStyle.setAlignment(HorizontalAlignment.CENTER);
            summaryStyle.setBorderBottom(BorderStyle.THIN);
            summaryStyle.setBorderTop(BorderStyle.THIN);
            summaryStyle.setBorderLeft(BorderStyle.THIN);
            summaryStyle.setBorderRight(BorderStyle.THIN);

            // Заголовки
            Row headerRow = sheet.createRow(0);
            String[] headers = {"№", "Дата", "День тижня", "Витрати (₴)", "Доходи (₴)", "Баланс (₴)", "Транзакцій"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, i == 0 ? 2000 : i == 6 ? 2500 : 3500);
            }

            // Дані
            int rowNum = 1;
            int index = 1;
            for (DayBudget budget : allBudgets) {
                Row row = sheet.createRow(rowNum++);

                // №
                Cell numCell = row.createCell(0);
                numCell.setCellValue(index++);
                numCell.setCellStyle(dateStyle);

                // Дата
                Cell dateCell = row.createCell(1);
                dateCell.setCellValue(dateFormatter.format(budget.getDate()));
                dateCell.setCellStyle(dateStyle);

                // День тижня
                Cell dayCell = row.createCell(2);
                dayCell.setCellValue(budget.getDayName() != null ? budget.getDayName() : "");
                dayCell.setCellStyle(dateStyle);

                // Витрати
                Cell expenseCell = row.createCell(3);
                expenseCell.setCellValue(budget.getTotalExpenses() != null ? budget.getTotalExpenses() : 0);
                expenseCell.setCellStyle(amountStyle);

                // Доходи
                Cell incomeCell = row.createCell(4);
                incomeCell.setCellValue(budget.getTotalIncomes() != null ? budget.getTotalIncomes() : 0);
                incomeCell.setCellStyle(amountStyle);

                // Баланс
                Cell balanceCell = row.createCell(5);
                Double balance = budget.getRemainingBalance() != null ? budget.getRemainingBalance() : 0;
                balanceCell.setCellValue(balance);
                balanceCell.setCellStyle(balance >= 0 ? balancePositiveStyle : balanceNegativeStyle);

                // Кількість транзакцій
                Cell countCell = row.createCell(6);
                int totalCount = (budget.getExpenses() != null ? budget.getExpenses().size() : 0)
                        + (budget.getIncomes() != null ? budget.getIncomes().size() : 0);
                countCell.setCellValue(totalCount);
                countCell.setCellStyle(dateStyle);
            }

            // Додаємо рядок з підсумками
            Row summaryRow = sheet.createRow(rowNum);

            // Об'єднуємо комірки для підпису
            Cell summaryLabel = summaryRow.createCell(0);
            summaryLabel.setCellValue("ПІДСУМКИ:");
            summaryLabel.setCellStyle(summaryStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 2));

            // Підраховуємо загальні суми
            double totalExpenses = allBudgets.stream()
                    .mapToDouble(b -> b.getTotalExpenses() != null ? b.getTotalExpenses() : 0)
                    .sum();
            double totalIncomes = allBudgets.stream()
                    .mapToDouble(b -> b.getTotalIncomes() != null ? b.getTotalIncomes() : 0)
                    .sum();
            double totalBalance = totalIncomes - totalExpenses;
            int totalTransactions = allBudgets.stream()
                    .mapToInt(b -> (b.getExpenses() != null ? b.getExpenses().size() : 0)
                            + (b.getIncomes() != null ? b.getIncomes().size() : 0))
                    .sum();

            Cell summaryExpense = summaryRow.createCell(3);
            summaryExpense.setCellValue(totalExpenses);
            summaryExpense.setCellStyle(amountStyle);

            Cell summaryIncome = summaryRow.createCell(4);
            summaryIncome.setCellValue(totalIncomes);
            summaryIncome.setCellStyle(amountStyle);

            Cell summaryBalance = summaryRow.createCell(5);
            summaryBalance.setCellValue(totalBalance);
            summaryBalance.setCellStyle(totalBalance >= 0 ? balancePositiveStyle : balanceNegativeStyle);

            Cell summaryCount = summaryRow.createCell(6);
            summaryCount.setCellValue(totalTransactions);
            summaryCount.setCellStyle(dateStyle);

            // Автоматичне підлаштування ширини колонок
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Додаємо трохи запасу
                int currentWidth = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(currentWidth + 500, 12000));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            byte[] bytes = out.toByteArray();

            HttpHeaders headers2 = new HttpHeaders();
            headers2.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            String filename = "all_days_" + LocalDate.now().format(dateFormatter) + ".xlsx";
            headers2.setContentDispositionFormData("attachment", filename);
            headers2.setContentLength(bytes.length);

            return ResponseEntity.ok().headers(headers2).body(bytes);
        }
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

    // ==================== ЕКСПОРТ ПІДСУМКІВ В EXCEL ====================

    @GetMapping("/export/totals")
    public ResponseEntity<byte[]> exportTotalsToExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws Exception {

        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();

        TotalBudget totalBudget = budgetService.getTotalBudget(startDate, endDate);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Підсумки");

            // Стилі
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle amountStyle = workbook.createCellStyle();
            amountStyle.setAlignment(HorizontalAlignment.RIGHT);
            amountStyle.setBorderBottom(BorderStyle.THIN);
            amountStyle.setBorderTop(BorderStyle.THIN);
            amountStyle.setBorderLeft(BorderStyle.THIN);
            amountStyle.setBorderRight(BorderStyle.THIN);
            amountStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            // Використовуємо PINK замість LIGHT_RED
            CellStyle expenseStyle = workbook.createCellStyle();
            expenseStyle.cloneStyleFrom(amountStyle);
            expenseStyle.setFillForegroundColor(IndexedColors.PINK.getIndex());
            expenseStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Використовуємо LIGHT_GREEN (доступний)
            CellStyle incomeStyle = workbook.createCellStyle();
            incomeStyle.cloneStyleFrom(amountStyle);
            incomeStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            incomeStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setAlignment(HorizontalAlignment.CENTER);
            dateStyle.setBorderBottom(BorderStyle.THIN);
            dateStyle.setBorderTop(BorderStyle.THIN);
            dateStyle.setBorderLeft(BorderStyle.THIN);
            dateStyle.setBorderRight(BorderStyle.THIN);

            CellStyle summaryStyle = workbook.createCellStyle();
            Font summaryFont = workbook.createFont();
            summaryFont.setBold(true);
            summaryStyle.setFont(summaryFont);
            summaryStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            summaryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            summaryStyle.setAlignment(HorizontalAlignment.CENTER);
            summaryStyle.setBorderBottom(BorderStyle.THIN);
            summaryStyle.setBorderTop(BorderStyle.THIN);
            summaryStyle.setBorderLeft(BorderStyle.THIN);
            summaryStyle.setBorderRight(BorderStyle.THIN);

            int rowNum = 0;

            // Заголовок
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Підсумки за період: " + startDate.format(dateFormatter) + " — " + endDate.format(dateFormatter));
            titleCell.setCellStyle(summaryStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

            rowNum++;

            // Загальні підсумки
            Row summaryRow1 = sheet.createRow(rowNum++);
            summaryRow1.createCell(0).setCellValue("Показник");
            summaryRow1.createCell(1).setCellValue("Сума");
            CellStyle headerLeft = workbook.createCellStyle();
            headerLeft.cloneStyleFrom(headerStyle);
            headerLeft.setAlignment(HorizontalAlignment.LEFT);
            summaryRow1.getCell(0).setCellStyle(headerLeft);

            Row expenseRow = sheet.createRow(rowNum++);
            expenseRow.createCell(0).setCellValue("Витрати:");
            expenseRow.createCell(1).setCellValue(totalBudget.getTotalExpenses() != null ? totalBudget.getTotalExpenses() : 0);
            expenseRow.getCell(1).setCellStyle(expenseStyle);

            Row incomeRow = sheet.createRow(rowNum++);
            incomeRow.createCell(0).setCellValue("Доходи:");
            incomeRow.createCell(1).setCellValue(totalBudget.getTotalIncomes() != null ? totalBudget.getTotalIncomes() : 0);
            incomeRow.getCell(1).setCellStyle(incomeStyle);

            Row balanceRow = sheet.createRow(rowNum++);
            balanceRow.createCell(0).setCellValue("Баланс:");
            Double balance = (totalBudget.getTotalIncomes() != null ? totalBudget.getTotalIncomes() : 0)
                    - (totalBudget.getTotalExpenses() != null ? totalBudget.getTotalExpenses() : 0);
            balanceRow.createCell(1).setCellValue(balance);
            balanceRow.getCell(1).setCellStyle(balance >= 0 ? incomeStyle : expenseStyle);

            rowNum++;

            // ===== ВИТРАТИ ЗА КАТЕГОРІЯМИ =====
            Row catHeaderRow = sheet.createRow(rowNum++);
            Cell catHeader1 = catHeaderRow.createCell(0);
            catHeader1.setCellValue("ВИТРАТИ ЗА КАТЕГОРІЯМИ");
            catHeader1.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 2));

            Row catSubHeader = sheet.createRow(rowNum++);
            catSubHeader.createCell(0).setCellValue("Категорія");
            catSubHeader.createCell(1).setCellValue("Сума");
            catSubHeader.createCell(2).setCellValue("Частка, %");
            catSubHeader.getCell(0).setCellStyle(headerStyle);
            catSubHeader.getCell(1).setCellStyle(headerStyle);
            catSubHeader.getCell(2).setCellStyle(headerStyle);

            Double totalExpenses = totalBudget.getTotalExpenses() != null ? totalBudget.getTotalExpenses() : 0;
            for (Map.Entry<Category, Double> entry : totalBudget.getExpensesByCategory().entrySet()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(entry.getKey().getName());
                row.createCell(1).setCellValue(entry.getValue());
                row.getCell(1).setCellStyle(amountStyle);
                double percent = totalExpenses > 0 ? (entry.getValue() / totalExpenses * 100) : 0;
                row.createCell(2).setCellValue(percent);
            }

            rowNum++;

            // ===== ДОХОДИ ЗА КАТЕГОРІЯМИ =====
            Row incomeCatHeader = sheet.createRow(rowNum++);
            Cell incomeCatHeader1 = incomeCatHeader.createCell(0);
            incomeCatHeader1.setCellValue("ДОХОДИ ЗА КАТЕГОРІЯМИ");
            incomeCatHeader1.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 2));

            Row incomeCatSubHeader = sheet.createRow(rowNum++);
            incomeCatSubHeader.createCell(0).setCellValue("Категорія");
            incomeCatSubHeader.createCell(1).setCellValue("Сума");
            incomeCatSubHeader.createCell(2).setCellValue("Частка, %");
            incomeCatSubHeader.getCell(0).setCellStyle(headerStyle);
            incomeCatSubHeader.getCell(1).setCellStyle(headerStyle);
            incomeCatSubHeader.getCell(2).setCellStyle(headerStyle);

            Double totalIncomes = totalBudget.getTotalIncomes() != null ? totalBudget.getTotalIncomes() : 0;
            for (Map.Entry<Category, Double> entry : totalBudget.getIncomesByCategory().entrySet()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(entry.getKey().getName());
                row.createCell(1).setCellValue(entry.getValue());
                row.getCell(1).setCellStyle(amountStyle);
                double percent = totalIncomes > 0 ? (entry.getValue() / totalIncomes * 100) : 0;
                row.createCell(2).setCellValue(percent);
            }

            // Автоматичне підлаштування ширини
            for (int i = 0; i < 3; i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(currentWidth + 500, 12000));
            }

            // ===== ЛИСТ 2: ДИНАМІКА ПО ДНЯХ =====
            Sheet dailySheet = workbook.createSheet("Динаміка по днях");
            int dailyRowNum = 0;

            Row dailyTitle = dailySheet.createRow(dailyRowNum++);
            Cell dailyTitleCell = dailyTitle.createCell(0);
            dailyTitleCell.setCellValue("Динаміка витрат та доходів по днях");
            dailyTitleCell.setCellStyle(summaryStyle);
            dailySheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            Row dailyHeader = dailySheet.createRow(dailyRowNum++);
            dailyHeader.createCell(0).setCellValue("Дата");
            dailyHeader.createCell(1).setCellValue("День тижня");
            dailyHeader.createCell(2).setCellValue("Витрати");
            dailyHeader.createCell(3).setCellValue("Доходи");
            dailyHeader.getCell(0).setCellStyle(headerStyle);
            dailyHeader.getCell(1).setCellStyle(headerStyle);
            dailyHeader.getCell(2).setCellStyle(headerStyle);
            dailyHeader.getCell(3).setCellStyle(headerStyle);

            for (TotalBudget.DayBudgetSummary summary : totalBudget.getDailySummaries().values()) {
                Row row = dailySheet.createRow(dailyRowNum++);
                row.createCell(0).setCellValue(summary.getDate() != null ? dateFormatter.format(summary.getDate()) : "");
                row.createCell(1).setCellValue(summary.getDayName() != null ? summary.getDayName() : "");
                row.createCell(2).setCellValue(summary.getExpenses() != null ? summary.getExpenses() : 0);
                row.getCell(2).setCellStyle(amountStyle);
                row.createCell(3).setCellValue(summary.getIncomes() != null ? summary.getIncomes() : 0);
                row.getCell(3).setCellStyle(amountStyle);
            }

            for (int i = 0; i < 4; i++) {
                dailySheet.autoSizeColumn(i);
                int currentWidth = dailySheet.getColumnWidth(i);
                dailySheet.setColumnWidth(i, Math.min(currentWidth + 500, 12000));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            byte[] bytes = out.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            String filename = "totals_" + startDate.format(dateFormatter) + "_" + endDate.format(dateFormatter) + ".xlsx";
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(bytes.length);

            return ResponseEntity.ok().headers(headers).body(bytes);
        }
    }
}