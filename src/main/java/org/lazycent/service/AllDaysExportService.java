package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.lazycent.model.DayBudget;
import org.lazycent.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Експорт днів у .xlsx — один аркуш «Дні».
 *
 * <p>Структура:
 * <ol>
 *   <li>Заголовок (назва + кількість записів)</li>
 *   <li><b>Блок Totals</b>: Поточний баланс / Найбільше витрат / Найбільше доходів</li>
 *   <li>Таблиця: № · Дата · День · Початковий баланс · Витрати · Доходи · Баланс · Транзакцій</li>
 *   <li>Футер (URL + timestamp)</li>
 * </ol>
 *
 * <p><b>Totals:</b>
 * <ul>
 *   <li>Поточний баланс — balance останнього дня (за датою), а не сума</li>
 *   <li>Найбільше витрат — день з MAX(totalExpenses)</li>
 *   <li>Найбільше доходів — день з MAX(totalIncomes)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AllDaysExportService {

    private final BudgetService budgetService;
    private final MessageSource messageSource;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] export(User user, Locale locale) throws IOException {
        List<DayBudget> days = budgetService.getAllBudgetsUnlimited(user);

        if (days.size() > 5000) {
            log.warn("Excel export: {} days — це може зайняти багато часу й пам'яті", days.size());
        }

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            buildSheet(wb, days, locale);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void buildSheet(XSSFWorkbook wb, List<DayBudget> days, Locale locale) {
        Sheet sheet = wb.createSheet(msg("allDays.export.title", locale));

        CellStyle titleStyle   = createTitleStyle(wb);
        CellStyle totalsLabel  = createTotalsLabelStyle(wb);
        CellStyle totalsValue  = createTotalsValueStyle(wb);
        CellStyle totalsValueM = createTotalsMoneyStyle(wb);
        CellStyle totalsSub    = createTotalsSubStyle(wb);
        CellStyle headerStyle  = createHeaderStyle(wb);
        CellStyle bodyStyle    = createBodyStyle(wb);
        CellStyle moneyStyle   = createMoneyStyle(wb);
        CellStyle centerStyle  = createCenterStyle(wb);
        CellStyle footerStyle  = createFooterStyle(wb);

        int row = 0;

        // ==================== TITLE ====================
        Row titleRow = sheet.createRow(row++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(msg("allDays.export.heading", locale, days.size()));
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        row++; // empty

        // ==================== TOTALS BLOCK ====================
        Totals t = calculateTotals(days);

        // "Поточний баланс"
        Row tb1 = sheet.createRow(row);
        Cell tb1Label = tb1.createCell(0);
        tb1Label.setCellValue(msg("allDays.totals.balance", locale));
        tb1Label.setCellStyle(totalsLabel);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 2));

        Cell tb1Val = tb1.createCell(3);
        tb1Val.setCellValue(t.currentBalance);
        tb1Val.setCellStyle(totalsValueM);

        Cell tb1Sub = tb1.createCell(4);
        tb1Sub.setCellValue(t.lastDate != null
                ? msg("allDays.export.asOf", locale,
                t.lastDate.format(DATE_FMT))
                : "—");
        tb1Sub.setCellStyle(totalsSub);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 4, 7));
        row++;

        // "Найбільше витрат"
        Row tb2 = sheet.createRow(row);
        Cell tb2Label = tb2.createCell(0);
        tb2Label.setCellValue(msg("allDays.totals.topExpense", locale));
        tb2Label.setCellStyle(totalsLabel);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 2));

        Cell tb2Val = tb2.createCell(3);
        tb2Val.setCellValue(t.maxExpense);
        tb2Val.setCellStyle(totalsValueM);

        Cell tb2Sub = tb2.createCell(4);
        tb2Sub.setCellValue(t.maxExpenseDate != null
                ? t.maxExpenseDate.format(DATE_FMT)
                : "—");
        tb2Sub.setCellStyle(totalsSub);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 4, 7));
        row++;

        // "Найбільше доходів"
        Row tb3 = sheet.createRow(row);
        Cell tb3Label = tb3.createCell(0);
        tb3Label.setCellValue(msg("allDays.totals.topIncome", locale));
        tb3Label.setCellStyle(totalsLabel);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 2));

        Cell tb3Val = tb3.createCell(3);
        tb3Val.setCellValue(t.maxIncome);
        tb3Val.setCellStyle(totalsValueM);

        Cell tb3Sub = tb3.createCell(4);
        tb3Sub.setCellValue(t.maxIncomeDate != null
                ? t.maxIncomeDate.format(DATE_FMT)
                : "—");
        tb3Sub.setCellStyle(totalsSub);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 4, 7));
        row++;

        row++; // empty

        // ==================== TABLE HEADER ====================
        String[] headers = {
                msg("allDays.export.col.number", locale),
                msg("allDays.export.col.date", locale),
                msg("allDays.export.col.day", locale),
                msg("allDays.export.col.starting", locale),
                msg("allDays.export.col.expenses", locale),
                msg("allDays.export.col.incomes", locale),
                msg("allDays.export.col.balance", locale),
                msg("allDays.export.col.count", locale)
        };
        Row headerRow = sheet.createRow(row++);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // ==================== TABLE DATA ====================
        int idx = 1;
        for (DayBudget d : days) {
            Row r = sheet.createRow(row++);

            Cell c0 = r.createCell(0);
            c0.setCellValue(idx++);
            c0.setCellStyle(centerStyle);

            Cell c1 = r.createCell(1);
            c1.setCellValue(d.getDate() != null ? d.getDate().format(DATE_FMT) : "");
            c1.setCellStyle(centerStyle);

            Cell c2 = r.createCell(2);
            c2.setCellValue(d.getDayName() != null ? d.getDayName() : "");
            c2.setCellStyle(bodyStyle);

            Cell c3 = r.createCell(3);
            c3.setCellValue(d.getStartingBalance() != null ? d.getStartingBalance() : 0.0);
            c3.setCellStyle(moneyStyle);

            Cell c4 = r.createCell(4);
            c4.setCellValue(d.getTotalExpenses());
            c4.setCellStyle(moneyStyle);

            Cell c5 = r.createCell(5);
            c5.setCellValue(d.getTotalIncomes());
            c5.setCellStyle(moneyStyle);

            Cell c6 = r.createCell(6);
            c6.setCellValue(d.getRemainingBalance() != null ? d.getRemainingBalance() : 0.0);
            c6.setCellStyle(moneyStyle);

            Cell c7 = r.createCell(7);
            c7.setCellValue(d.getExpenses().size() + d.getIncomes().size());
            c7.setCellStyle(centerStyle);
        }

        // ==================== FOOTER ====================
        row++;
        Row footerRow = sheet.createRow(row);
        Cell footerCell = footerRow.createCell(0);
        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        footerCell.setCellValue(msg("allDays.export.footer", locale, appUrl, timestamp));
        footerCell.setCellStyle(footerStyle);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 7));

        // ==================== COLUMN WIDTHS ====================
        sheet.setColumnWidth(0, 6 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 14 * 256);
        sheet.setColumnWidth(3, 18 * 256);
        sheet.setColumnWidth(4, 16 * 256);
        sheet.setColumnWidth(5, 16 * 256);
        sheet.setColumnWidth(6, 16 * 256);
        sheet.setColumnWidth(7, 12 * 256);

        // Freeze: заголовок + 3 totals + пуста строка + header таблиці = 6 рядків
        sheet.createFreezePane(0, 7);
    }

    /**
     * Рахує Totals: поточний баланс (останній день за датою),
     * макс. витрати / макс. доходи (по всіх днях).
     */
    private Totals calculateTotals(List<DayBudget> days) {
        Totals t = new Totals();
        if (days == null || days.isEmpty()) return t;

        // Поточний баланс — balance ОСТАННЬОГО дня (max date)
        DayBudget last = days.stream()
                .filter(d -> d.getDate() != null)
                .max(Comparator.comparing(DayBudget::getDate))
                .orElse(null);
        if (last != null) {
            t.currentBalance = last.getRemainingBalance() != null
                    ? last.getRemainingBalance() : 0.0;
            t.lastDate = last.getDate();
        }

        // Max expenses / max income
        double maxExp = Double.NEGATIVE_INFINITY;
        double maxInc = Double.NEGATIVE_INFINITY;

        for (DayBudget d : days) {
            double exp = d.getTotalExpenses();
            double inc = d.getTotalIncomes();

            if (exp > maxExp) {
                maxExp = exp;
                t.maxExpenseDate = d.getDate();
            }
            if (inc > maxInc) {
                maxInc = inc;
                t.maxIncomeDate = d.getDate();
            }
        }

        t.maxExpense = maxExp == Double.NEGATIVE_INFINITY ? 0.0 : maxExp;
        t.maxIncome  = maxInc == Double.NEGATIVE_INFINITY ? 0.0 : maxInc;
        return t;
    }

    /** Внутрішній DTO для Totals. */
    private static class Totals {
        double currentBalance = 0.0;
        java.time.LocalDate lastDate = null;
        double maxExpense = 0.0;
        java.time.LocalDate maxExpenseDate = null;
        double maxIncome = 0.0;
        java.time.LocalDate maxIncomeDate = null;
    }

    // ==================== STYLES ====================

    private CellStyle createTitleStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        s.setFont(f);
        return s;
    }

    private CellStyle createTotalsLabelStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 11);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        return s;
    }

    private CellStyle createTotalsValueStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
    }

    private CellStyle createTotalsMoneyStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private CellStyle createTotalsSubStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setFontHeightInPoints((short) 9);
        f.setItalic(true);
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        return s;
    }

    private CellStyle createBodyStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.HAIR);
        s.setBorderRight(BorderStyle.HAIR);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        return s;
    }

    private CellStyle createCenterStyle(XSSFWorkbook wb) {
        CellStyle s = createBodyStyle(wb);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private CellStyle createMoneyStyle(XSSFWorkbook wb) {
        CellStyle s = createBodyStyle(wb);
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }

    private CellStyle createFooterStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setFontHeightInPoints((short) 9);
        f.setItalic(true);
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFont(f);
        s.setBorderTop(BorderStyle.THIN);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setWrapText(true);
        return s;
    }

    private String msg(String key, Locale locale, Object... args) {
        try {
            return messageSource.getMessage(key, args, locale);
        } catch (Exception e) {
            log.warn("Missing i18n key '{}' for locale {}", key, locale);
            return key;
        }
    }
}