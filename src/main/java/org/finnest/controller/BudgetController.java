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
    public String duplicateExpense(@PathVariable Long id) {
        try {
            LocalDate date = budgetService.duplicateExpense(id);
            return "redirect:/day/" + date;
        } catch (Exception e) {
            log.error("Error duplicating expense: ", e);
            return "redirect:/?error=duplicate_failed";
        }
    }

    @PostMapping("/income/duplicate/{id}")
    public String duplicateIncome(@PathVariable Long id) {
        try {
            LocalDate date = budgetService.duplicateIncome(id);
            return "redirect:/day/" + date;
        } catch (Exception e) {
            log.error("Error duplicating income: ", e);
            return "redirect:/?error=duplicate_failed";
        }
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
            model.addAttribute("expenseCategories", budgetService.getExpenseCategories());
            model.addAttribute("incomeCategories", budgetService.getIncomeCategories());
            return "day-detail";
        } catch (Exception e) {
            log.error("Error loading day page: ", e);
            model.addAttribute("error", "Помилка завантаження дня: " + e.getMessage());
            return "error";
        }
    }

    // ==================== ВСІ ДНІ ====================
    @GetMapping("/all-days")
    public String viewAllDays(Model model) {
        log.info("Viewing all days");
        try {
            List<DayBudget> allBudgets = budgetService.getAllBudgets();
            model.addAttribute("allBudgets", allBudgets);
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
            @RequestParam(value = "seller", required = false) String seller) {
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
            expense.setBuyer(buyer);
            expense.setSeller(seller);
            budgetService.addExpense(dayBudget.getId(), expense);
            return "redirect:/day/" + date + "?success=expense_added";
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
            @RequestParam(value = "time", required = false) String timeStr,
            @RequestParam(value = "buyer", required = false) String buyer,
            @RequestParam(value = "seller", required = false) String seller) {
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
            income.setBuyer(buyer);
            income.setSeller(seller);
            budgetService.addIncome(dayBudget.getId(), income);
            return "redirect:/day/" + date + "?success=income_added";
        } catch (Exception e) {
            log.error("Error adding income: ", e);
            return "redirect:/day/" + date + "?error=true";
        }
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
            @RequestParam(value = "seller", required = false) String seller) {
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
            expense.setBuyer(buyer);
            expense.setSeller(seller);
            budgetService.addExpense(dayBudget.getId(), expense);
            return "redirect:/budget?success=expense_added";
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
            @RequestParam(value = "time", required = false) String timeStr,
            @RequestParam(value = "buyer", required = false) String buyer,
            @RequestParam(value = "seller", required = false) String seller) {
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
            income.setBuyer(buyer);
            income.setSeller(seller);
            budgetService.addIncome(dayBudget.getId(), income);
            return "redirect:/budget?success=income_added";
        } catch (Exception e) {
            log.error("Error in quick income: ", e);
            return "redirect:/?error=income_failed";
        }
    }

    // ==================== РЕДАГУВАННЯ / ВИДАЛЕННЯ ====================
    @PostMapping("/expense/delete/{id}")
    public String deleteExpense(@PathVariable Long id) {
        try {
            LocalDate date = budgetService.getDateByExpense(id);
            budgetService.deleteExpense(id);
            return "redirect:/day/" + date + "?success=expense_deleted";
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
            return "redirect:/day/" + date + "?success=income_deleted";
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
            @RequestParam(value = "time", required = false) String timeStr,
            @RequestParam(value = "buyer", required = false) String buyer,
            @RequestParam(value = "seller", required = false) String seller) {
        try {
            LocalTime time = null;
            if (timeStr != null && !timeStr.isEmpty()) {
                time = LocalTime.parse(timeStr);
            }
            budgetService.updateExpense(id, description, amount, categoryId, quantity, details, shareWithFamily, time, buyer, seller);
            LocalDate date = budgetService.getDateByExpense(id);
            return "redirect:/day/" + date + "?success=expense_edited";
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
            @RequestParam(value = "time", required = false) String timeStr,
            @RequestParam(value = "buyer", required = false) String buyer,
            @RequestParam(value = "seller", required = false) String seller) {
        try {
            LocalTime time = null;
            if (timeStr != null && !timeStr.isEmpty()) {
                time = LocalTime.parse(timeStr);
            }
            budgetService.updateIncome(id, description, amount, categoryId, quantity, details, shareWithFamily, time, buyer, seller);
            LocalDate date = budgetService.getDateByIncome(id);
            return "redirect:/day/" + date + "?success=income_edited";
        } catch (Exception e) {
            log.error("Error editing income: ", e);
            return "redirect:/?error=edit_failed";
        }
    }

    // ==================== КАТЕГОРІЇ ====================
    @PostMapping("/categories/add")
    public String addCategory(@RequestParam String name, @RequestParam String type) {
        try {
            budgetService.addCategory(name, type);
            return "redirect:/settings?success=category_added";
        } catch (Exception e) {
            log.error("Error adding category: ", e);
            return "redirect:/settings?error=category_add_failed";
        }
    }

    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable Long id) {
        try {
            budgetService.deleteCategory(id);
            return "redirect:/settings?success=category_deleted";
        } catch (Exception e) {
            log.error("Error deleting category: ", e);
            return "redirect:/settings?error=category_delete_failed";
        }
    }

    @PostMapping("/categories/update/{id}")
    public String updateCategory(@PathVariable Long id, @RequestParam String name) {
        try {
            budgetService.updateCategory(id, name);
            return "redirect:/settings?success=category_updated";
        } catch (Exception e) {
            log.error("Error updating category: ", e);
            return "redirect:/settings?error=category_update_failed";
        }
    }

    // ==================== ЕКСПОРТ В EXCEL ====================
    @GetMapping("/export/day/{date}")
    public ResponseEntity<byte[]> exportDayToExcel(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
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

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Транзакції " + date.format(dateFormatter));

            // Стилі
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
            workbook.close();

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            responseHeaders.setContentDispositionFormData("attachment", "transactions_" + date + ".xlsx");

            return new ResponseEntity<>(baos.toByteArray(), responseHeaders, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error exporting day to Excel: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== ТЕСТ ====================
    @GetMapping("/init-demo-test")
    @ResponseBody
    public String initDemoTest() {
        try {
            budgetService.initializeDemoData();
            return "Demo data initialized successfully!";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
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