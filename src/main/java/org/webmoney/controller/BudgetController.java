package org.webmoney.controller;

import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.webmoney.model.*;
import org.webmoney.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class BudgetController {

    private static final Logger log = LoggerFactory.getLogger(BudgetController.class);
    private final BudgetService budgetService;

    @GetMapping("/export/day/{date}")
    public ResponseEntity<byte[]> exportDayToExcel(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) throws IOException {

        DayBudget dayBudget = budgetService.getOrCreateDayBudget(date);

        // Створюємо Excel-книгу
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Бюджет " + date);

        // Стилі
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Заголовок
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Бюджет за " + date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " (" + dayBudget.getDayName() + ")");
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        // Заголовки таблиці
        String[] headers = {"Час", "Назва", "Кількість", "Сума", "Деталі", "Категорія", "Тип"};
        Row headerRow = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.autoSizeColumn(i);
        }

        // Дані
        int rowNum = 2;

        // Витрати
        for (Expense expense : dayBudget.getExpenses()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(expense.getTime() != null ? expense.getTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "");
            row.createCell(1).setCellValue(expense.getDescription());
            row.createCell(2).setCellValue(expense.getQuantity() != null ? expense.getQuantity() : 1);
            row.createCell(3).setCellValue(expense.getAmount());
            row.createCell(4).setCellValue(expense.getDetails() != null ? expense.getDetails() : "");
            row.createCell(5).setCellValue(expense.getCategory().getName());
            row.createCell(6).setCellValue("Витрата");
        }

        // Доходи
        for (Income income : dayBudget.getIncomes()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(income.getTime() != null ? income.getTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "");
            row.createCell(1).setCellValue(income.getDescription());
            row.createCell(2).setCellValue(income.getQuantity() != null ? income.getQuantity() : 1);
            row.createCell(3).setCellValue(income.getAmount());
            row.createCell(4).setCellValue(income.getDetails() != null ? income.getDetails() : "");
            row.createCell(5).setCellValue(income.getCategory().getName());
            row.createCell(6).setCellValue("Дохід");
        }

        // Підсумки
        Row summaryRow = sheet.createRow(rowNum + 1);
        summaryRow.createCell(0).setCellValue("Підсумки:");
        summaryRow.createCell(3).setCellValue("Доходи: " + dayBudget.getTotalIncomes());
        summaryRow.createCell(4).setCellValue("Витрати: " + dayBudget.getTotalExpenses());
        summaryRow.createCell(5).setCellValue("Баланс: " + dayBudget.getRemainingBalance());

        // Автоматичне підлаштування ширини колонок
        for (int i = 0; i < 7; i++) {
            sheet.autoSizeColumn(i);
        }

        // Запис у байтовий масив
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        // Формуємо відповідь
        String filename = "budget_" + date + ".xlsx";
        HttpHeaders headersResp = new HttpHeaders();
        headersResp.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headersResp.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return new ResponseEntity<>(out.toByteArray(), headersResp, HttpStatus.OK);
    }

    // ==================== ГОЛОВНА СТОРІНКА ====================
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
            // Додаємо категорії для модального вікна
            model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
            model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
            return "day-detail";
        } catch (Exception e) {
            log.error("Error loading day page: ", e);
            model.addAttribute("error", "Помилка завантаження дня: " + e.getMessage());
            return "error";
        }
    }

    // ==================== ВСІ ДНІ (оновлено з передачею категорій) ====================
    @GetMapping("/all-days")
    public String viewAllDays(Model model) {
        log.info("Viewing all days");
        try {
            List<DayBudget> allBudgets = budgetService.getAllBudgets();
            model.addAttribute("allBudgets", allBudgets);
            // Додаємо категорії для модального вікна ↓↓↓
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

    // ==================== НАЛАШТУВАННЯ (категорії) ====================
    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("categories", budgetService.getAllCategories());
        model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
        model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
        return "settings";
    }

    // ==================== ДОДАВАННЯ ТРАНЗАКЦІЙ (швидке) ====================
    @PostMapping("/quick-expense")
    public String quickAddExpense(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam("category") Long categoryId,
            @RequestParam("description") String description,
            @RequestParam("amount") Double amount,
            @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
            @RequestParam(value = "details", required = false) String details,
            @RequestParam(value = "shareWithFamily", required = false, defaultValue = "false") Boolean shareWithFamily,
            @RequestParam(value = "time", required = false) String timeStr) {
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
            @RequestParam(value = "time", required = false) String timeStr) {
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
            budgetService.addIncome(dayBudget.getId(), income);
            return "redirect:/budget";
        } catch (Exception e) {
            log.error("Error in quick income: ", e);
            return "redirect:/?error=income_failed";
        }
    }

    // ==================== ДОДАВАННЯ ЗІ СТОРІНКИ ДНЯ ====================

    @PostMapping("/day/{date}/expense")
    public String addExpense(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Long categoryId,
            @RequestParam String description,
            @RequestParam Double amount,
            @RequestParam(value = "quantity", required = false, defaultValue = "1") Integer quantity,
            @RequestParam(value = "details", required = false) String details,
            @RequestParam(value = "shareWithFamily", required = false, defaultValue = "false") Boolean shareWithFamily,
            @RequestParam(value = "time", required = false) String timeStr) {
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
            @RequestParam(value = "time", required = false) String timeStr) {
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
            budgetService.addIncome(dayBudget.getId(), income);
            return "redirect:/day/" + date;
        } catch (Exception e) {
            log.error("Error adding income: ", e);
            return "redirect:/day/" + date + "?error=true";
        }
    }

    // ==================== РЕДАГУВАННЯ ТА ВИДАЛЕННЯ ====================
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
            @RequestParam(value = "time", required = false) String timeStr) {
        try {
            LocalTime time = null;
            if (timeStr != null && !timeStr.isEmpty()) {
                time = LocalTime.parse(timeStr);
            }
            budgetService.updateExpense(id, description, amount, categoryId, quantity, details, shareWithFamily, time);
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
            @RequestParam(value = "time", required = false) String timeStr) {
        try {
            LocalTime time = null;
            if (timeStr != null && !timeStr.isEmpty()) {
                time = LocalTime.parse(timeStr);
            }
            budgetService.updateIncome(id, description, amount, categoryId, quantity, details, shareWithFamily, time);
            LocalDate date = budgetService.getDateByIncome(id);
            return "redirect:/day/" + date;
        } catch (Exception e) {
            log.error("Error editing income: ", e);
            return "redirect:/?error=edit_failed";
        }
    }

    // ==================== УПРАВЛІННЯ КАТЕГОРІЯМИ ====================
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

    @GetMapping("/categories/expense")
    @ResponseBody
    public List<Category> getExpenseCategories() {
        return budgetService.getExpenseCategories();
    }

    @GetMapping("/categories/income")
    @ResponseBody
    public List<Category> getIncomeCategories() {
        return budgetService.getIncomeCategories();
    }

    // ==================== ТЕСТОВИЙ ЕНДПОІНТ ====================
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
}