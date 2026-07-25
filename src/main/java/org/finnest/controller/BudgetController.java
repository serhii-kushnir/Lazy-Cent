package org.finnest.controller;

import org.finnest.model.*;
import org.finnest.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class BudgetController {

    private static final Logger log = LoggerFactory.getLogger(BudgetController.class);
    private final BudgetService budgetService;

    // ==================== ДУБЛЮВАННЯ ТРАНЗАКЦІЙ ====================

    @PostMapping("/expense/duplicate/{id}")
    public String duplicateExpense(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        LocalDate date = budgetService.duplicateExpense(id);
        redirectAttributes.addFlashAttribute("successMessage", "Витрату дубльовано!");
        return "redirect:/day/" + date;
    }

    @PostMapping("/income/duplicate/{id}")
    public String duplicateIncome(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        LocalDate date = budgetService.duplicateIncome(id);
        redirectAttributes.addFlashAttribute("successMessage", "Дохід дубльовано!");
        return "redirect:/day/" + date;
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
    }

    // ==================== ДЕНЬ ====================
    @GetMapping("/day/{date}")
    public String viewDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
        model.addAttribute("dayBudget", dayBudget);
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
        return "day-detail";
    }

    // ==================== ВСІ ДНІ ====================
    @GetMapping("/all-days")
    public String viewAllDays(Model model) {
        log.info("Viewing all days");
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
        log.info("Accessing totals page");
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
            @RequestParam(value = "seller", required = false) String seller,
            RedirectAttributes redirectAttributes) {

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

        redirectAttributes.addFlashAttribute("successMessage", "Витрату додано!");
        return "redirect:/day/" + date;
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
            @RequestParam(value = "seller", required = false) String seller,
            RedirectAttributes redirectAttributes) {

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

        redirectAttributes.addFlashAttribute("successMessage", "Дохід додано!");
        return "redirect:/day/" + date;
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
            @RequestParam(value = "seller", required = false) String seller,
            RedirectAttributes redirectAttributes) {

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

        redirectAttributes.addFlashAttribute("successMessage", "Витрату додано!");
        return "redirect:/budget";
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
            @RequestParam(value = "seller", required = false) String seller,
            RedirectAttributes redirectAttributes) {

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

        redirectAttributes.addFlashAttribute("successMessage", "Дохід додано!");
        return "redirect:/budget";
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

        LocalTime time = null;
        if (timeStr != null && !timeStr.isEmpty()) {
            time = LocalTime.parse(timeStr);
        }
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

        LocalTime time = null;
        if (timeStr != null && !timeStr.isEmpty()) {
            time = LocalTime.parse(timeStr);
        }
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
    public ResponseEntity<byte[]> exportDayToExcel(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        List<Expense> expenses = new ArrayList<>(dayBudget.getExpenses());
        List<Income> incomes = new ArrayList<>(dayBudget.getIncomes());

        // Сортування за часом (спадання)
        expenses.sort((e1, e2) -> {
            LocalTime t1 = e1.getTime();
            LocalTime t2 = e2.getTime();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });
        incomes.sort((i1, i2) -> {
            LocalTime t1 = i1.getTime();
            LocalTime t2 = i2.getTime();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Транзакції " + date.format(dateFormatter));

            // Стилі (залишаємо без змін)
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            Font expenseFont = workbook.createFont();
            expenseFont.setColor(IndexedColors.RED.getIndex());
            CellStyle expenseStyle = workbook.createCellStyle();
            expenseStyle.setFont(expenseFont);
            expenseStyle.setAlignment(HorizontalAlignment.CENTER);

            Font incomeFont = workbook.createFont();
            incomeFont.setColor(IndexedColors.GREEN.getIndex());
            CellStyle incomeStyle = workbook.createCellStyle();
            incomeStyle.setFont(incomeFont);
            incomeStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle defaultStyle = workbook.createCellStyle();
            defaultStyle.setAlignment(HorizontalAlignment.CENTER);
            defaultStyle.setBorderBottom(BorderStyle.THIN);
            defaultStyle.setBorderLeft(BorderStyle.THIN);
            defaultStyle.setBorderRight(BorderStyle.THIN);
            defaultStyle.setBorderTop(BorderStyle.THIN);

            Font summaryFont = workbook.createFont();
            summaryFont.setBold(true);
            CellStyle summaryStyle = workbook.createCellStyle();
            summaryStyle.setFont(summaryFont);
            summaryStyle.setAlignment(HorizontalAlignment.CENTER);

            // Заголовок
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            String dayName = dayBudget.getDayName() != null ? dayBudget.getDayName() : "";
            titleCell.setCellValue("Транзакції за " + date.format(dateFormatter) + " (" + dayName + ")");
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 8));

            // Заголовки колонок
            String[] headers = {"Тип", "Час", "Найменування", "Кількість", "Сума", "Покупець", "Продавець", "Деталі", "Категорія"};
            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 2;

            // Витрати
            for (Expense expense : expenses) {
                Row row = sheet.createRow(rowNum++);
                Cell typeCell = row.createCell(0);
                typeCell.setCellValue("Витрата");
                typeCell.setCellStyle(expenseStyle);

                row.createCell(1).setCellValue(expense.getTime() != null ? expense.getTime().toString() : "");
                row.createCell(2).setCellValue(expense.getDescription());
                row.createCell(3).setCellValue(expense.getQuantity() != null ? expense.getQuantity() : 1);
                Cell amountCell = row.createCell(4);
                amountCell.setCellValue(expense.getAmount());
                amountCell.setCellStyle(expenseStyle);
                row.createCell(5).setCellValue(expense.getBuyer() != null ? expense.getBuyer() : "");
                row.createCell(6).setCellValue(expense.getSeller() != null ? expense.getSeller() : "");
                row.createCell(7).setCellValue(expense.getDetails() != null ? expense.getDetails() : "");
                row.createCell(8).setCellValue(expense.getCategory().getName());

                for (int i = 1; i < 9; i++) {
                    Cell cell = row.getCell(i);
                    if (cell != null) cell.setCellStyle(defaultStyle);
                }
            }

            // Доходи
            for (Income income : incomes) {
                Row row = sheet.createRow(rowNum++);
                Cell typeCell = row.createCell(0);
                typeCell.setCellValue("Дохід");
                typeCell.setCellStyle(incomeStyle);

                row.createCell(1).setCellValue(income.getTime() != null ? income.getTime().toString() : "");
                row.createCell(2).setCellValue(income.getDescription());
                row.createCell(3).setCellValue(income.getQuantity() != null ? income.getQuantity() : 1);
                Cell amountCell = row.createCell(4);
                amountCell.setCellValue(income.getAmount());
                amountCell.setCellStyle(incomeStyle);
                row.createCell(5).setCellValue(income.getBuyer() != null ? income.getBuyer() : "");
                row.createCell(6).setCellValue(income.getSeller() != null ? income.getSeller() : "");
                row.createCell(7).setCellValue(income.getDetails() != null ? income.getDetails() : "");
                row.createCell(8).setCellValue(income.getCategory().getName());

                for (int i = 1; i < 9; i++) {
                    Cell cell = row.getCell(i);
                    if (cell != null) cell.setCellStyle(defaultStyle);
                }
            }

            // Підсумки
            if (rowNum > 2) {
                Row emptyRow = sheet.createRow(rowNum++);
                for (int i = 0; i < 9; i++) emptyRow.createCell(i).setCellValue("");

                Row startRow = sheet.createRow(rowNum++);
                startRow.createCell(0).setCellValue("Початковий баланс:");
                double startBal = dayBudget.getStartingBalance() != null ? dayBudget.getStartingBalance() : 0.0;
                startRow.createCell(4).setCellValue(String.format("%.2f", startBal));
                for (int i = 0; i < 9; i++) {
                    Cell cell = startRow.getCell(i);
                    if (cell == null) cell = startRow.createCell(i);
                    cell.setCellStyle(summaryStyle);
                }

                Row sumRow1 = sheet.createRow(rowNum++);
                sumRow1.createCell(0).setCellValue("Підсумки:");
                sumRow1.createCell(4).setCellValue("Всього витрат: " + String.format("%.2f", dayBudget.getTotalExpenses()));
                for (int i = 0; i < 9; i++) {
                    Cell cell = sumRow1.getCell(i);
                    if (cell == null) cell = sumRow1.createCell(i);
                    cell.setCellStyle(summaryStyle);
                }

                Row sumRow2 = sheet.createRow(rowNum++);
                sumRow2.createCell(4).setCellValue("Всього доходів: " + String.format("%.2f", dayBudget.getTotalIncomes()));
                for (int i = 0; i < 9; i++) {
                    Cell cell = sumRow2.getCell(i);
                    if (cell == null) cell = sumRow2.createCell(i);
                    cell.setCellStyle(summaryStyle);
                }

                Row sumRow3 = sheet.createRow(rowNum++);
                sumRow3.createCell(4).setCellValue("Баланс: " + String.format("%.2f", dayBudget.getRemainingBalance()));
                for (int i = 0; i < 9; i++) {
                    Cell cell = sumRow3.getCell(i);
                    if (cell == null) cell = sumRow3.createCell(i);
                    cell.setCellStyle(summaryStyle);
                }
            } else {
                Row emptyRow = sheet.createRow(rowNum);
                emptyRow.createCell(0).setCellValue("Немає транзакцій за цей день");
                for (int i = 1; i < 9; i++) emptyRow.createCell(i).setCellValue("");
            }

            for (int i = 0; i < 9; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            responseHeaders.setContentDispositionFormData("attachment", "transactions_" + date + ".xlsx");

            return new ResponseEntity<>(baos.toByteArray(), responseHeaders, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error exporting day to Excel: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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