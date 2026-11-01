package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.lazycent.model.User;
import org.lazycent.model.dto.BudgetDashboardDto;
import org.lazycent.model.dto.CategoryBudgetDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.apache.poi.ss.usermodel.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Генерує .xlsx файл з даними бюджету на обраний місяць.
 * Використовує Apache POI (XSSFWorkbook = .xlsx).
 *
 * <p>У футері додається джерело сайту та час генерації — щоб файл
 * було легко ідентифікувати при відправці/друці.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetExportService {

    private final BudgetPlanningService budgetPlanningService;
    private final MessageSource messageSource;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public byte[] export(User user, int month, int year, Locale locale) throws IOException {
        BudgetDashboardDto dashboard = budgetPlanningService.getDashboard(user, month, year);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            buildSheet(workbook, dashboard, month, year, locale);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void buildSheet(XSSFWorkbook wb, BudgetDashboardDto d,
                            int month, int year, Locale locale) {
        Sheet sheet = wb.createSheet(msg("budget.title", locale));

        CellStyle titleStyle = createTitleStyle(wb);
        CellStyle labelStyle = createLabelStyle(wb);
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle bodyStyle = createBodyStyle(wb);
        CellStyle moneyStyle = createMoneyStyle(wb);
        CellStyle boldMoneyStyle = createBoldMoneyStyle(wb);
        CellStyle percentStyle = createPercentStyle(wb);
        CellStyle footerStyle = createFooterStyle(wb);

        int row = 0;

        // ----- Title -----
        Row titleRow = sheet.createRow(row++);
        String monthYear = String.format("%02d.%d", month, year);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(msg("budget.heading", locale, monthYear));
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        row++; // empty

        // ----- Summary -----
        row = addSummaryRow(sheet, row, msg("budget.totalBudget", locale),
                d.getTotalBudget(), labelStyle, boldMoneyStyle);
        row = addSummaryRow(sheet, row, msg("budget.spent", locale),
                d.getTotalExpenses(), labelStyle, boldMoneyStyle);
        row = addSummaryRow(sheet, row, msg("budget.remaining", locale),
                d.getRemainingBudget(), labelStyle, boldMoneyStyle);
        row = addSummaryRow(sheet, row, msg("budget.projected", locale),
                d.getProjectedBalance(), labelStyle, boldMoneyStyle);

        row++; // empty

        // ----- Header -----
        Row headerRow = sheet.createRow(row++);
        String[] headers = {
                msg("budget.col.category", locale),
                msg("budget.col.spent", locale),
                msg("budget.col.limit", locale),
                msg("budget.col.progress", locale),
                msg("budget.col.status", locale),
                msg("budget.col.note", locale)
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // ----- Data -----
        for (CategoryBudgetDto cat : d.getCategories()) {
            Row r = sheet.createRow(row++);

            Cell catCell = r.createCell(0);
            catCell.setCellValue(cat.getCategory().getName());
            catCell.setCellStyle(bodyStyle);

            Cell spentCell = r.createCell(1);
            spentCell.setCellValue(cat.getSpent());
            spentCell.setCellStyle(moneyStyle);

            Cell limitCell = r.createCell(2);
            if (cat.getLimit() != null && cat.getLimit() > 0) {
                limitCell.setCellValue(cat.getLimit());
                limitCell.setCellStyle(moneyStyle);
            } else {
                limitCell.setCellValue("—");
                limitCell.setCellStyle(bodyStyle);
            }

            Cell pctCell = r.createCell(3);
            pctCell.setCellValue(cat.getPercentUsed() != null ? cat.getPercentUsed() / 100 : 0);
            pctCell.setCellStyle(percentStyle);

            Cell statusCell = r.createCell(4);
            statusCell.setCellValue(localizedStatus(cat.getStatus(), locale));
            statusCell.setCellStyle(bodyStyle);

            Cell noteCell = r.createCell(5);
            noteCell.setCellValue(cat.getNote() != null ? cat.getNote() : "");
            noteCell.setCellStyle(bodyStyle);
        }

        // ===== Footer з джерелом сайту =====
        row++; // empty
        Row footerRow = sheet.createRow(row);
        Cell footerCell = footerRow.createCell(0);

        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        footerCell.setCellValue(msg("budget.export.footer", locale, appUrl, timestamp));
        footerCell.setCellStyle(footerStyle);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 5));

        // ----- Column widths -----
        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 15 * 256);
        sheet.setColumnWidth(2, 15 * 256);
        sheet.setColumnWidth(3, 10 * 256);
        sheet.setColumnWidth(4, 15 * 256);
        sheet.setColumnWidth(5, 40 * 256);
    }

    private int addSummaryRow(Sheet sheet, int rowNum, String label, Double value,
                              CellStyle labelStyle, CellStyle moneyStyle) {
        Row r = sheet.createRow(rowNum);
        Cell l = r.createCell(0);
        l.setCellValue(label);
        l.setCellStyle(labelStyle);

        Cell v = r.createCell(1);
        if (value != null) v.setCellValue(value);
        v.setCellStyle(moneyStyle);

        return rowNum + 1;
    }

    private String localizedStatus(String status, Locale locale) {
        if (status == null) return "";
        return switch (status) {
            case "OK" -> msg("budget.status.ok", locale);
            case "WARNING" -> msg("budget.status.warning", locale);
            case "OVER" -> msg("budget.status.over", locale);
            default -> status;
        };
    }

    // ===== Styles =====

    private CellStyle createTitleStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 16);
        s.setFont(f);
        return s;
    }

    private CellStyle createLabelStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(false);
        s.setFont(f);
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
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
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

    private CellStyle createMoneyStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        s.setBorderBottom(BorderStyle.THIN);
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }

    private CellStyle createBoldMoneyStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setBorderBottom(BorderStyle.THIN);
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }

    private CellStyle createPercentStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("0.0%"));
        s.setBorderBottom(BorderStyle.THIN);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    /**
     * Стиль футера: дрібний, сірий, курсив, з верхньою рамкою.
     */
    private CellStyle createFooterStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setFontHeightInPoints((short) 9);
        f.setItalic(true);
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFont(f);
        s.setBorderTop(BorderStyle.THIN);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
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

    // ==================== INCOME EXPORT ====================

    public byte[] exportIncome(User user, int month, int year, Locale locale) throws IOException {
        BudgetDashboardDto dashboard = budgetPlanningService.getIncomeDashboard(user, month, year);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            buildIncomeSheet(workbook, dashboard, month, year, locale);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void buildIncomeSheet(XSSFWorkbook wb, BudgetDashboardDto d,
                                  int month, int year, Locale locale) {
        Sheet sheet = wb.createSheet(msg("incomeBudget.title", locale));

        CellStyle titleStyle       = createTitleStyle(wb);
        CellStyle labelStyle       = createLabelStyle(wb);
        CellStyle headerStyle      = createHeaderStyle(wb);
        CellStyle bodyStyle        = createBodyStyle(wb);
        CellStyle moneyStyle       = createMoneyStyle(wb);
        CellStyle boldMoneyStyle   = createBoldMoneyStyle(wb);
        CellStyle percentStyle     = createPercentStyle(wb);
        CellStyle footerStyle      = createFooterStyle(wb);

        int row = 0;

        // ----- Title -----
        Row titleRow = sheet.createRow(row++);
        String monthYear = String.format("%02d.%d", month, year);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(msg("incomeBudget.heading", locale, monthYear));
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        row++; // empty

        // ----- Summary -----
        row = addSummaryRow(sheet, row, msg("incomeBudget.totalTarget", locale),
                d.getTotalBudget(), labelStyle, boldMoneyStyle);
        row = addSummaryRow(sheet, row, msg("incomeBudget.received", locale),
                d.getTotalIncomes(), labelStyle, boldMoneyStyle);

        // Залишок або перевиконання
        String remainingLabel = (d.getRemainingBudget() != null && d.getRemainingBudget() <= 0)
                ? msg("incomeBudget.overachieved", locale)
                : msg("incomeBudget.remaining", locale);
        Double remainingAbs = d.getRemainingBudget() != null
                ? Math.abs(d.getRemainingBudget()) : 0.0;
        row = addSummaryRow(sheet, row, remainingLabel, remainingAbs, labelStyle, boldMoneyStyle);

        row = addSummaryRow(sheet, row, msg("incomeBudget.projected", locale),
                d.getProjectedTotalExpenses(), labelStyle, boldMoneyStyle);

        row++; // empty

        // ----- Header -----
        Row headerRow = sheet.createRow(row++);
        String[] headers = {
                msg("incomeBudget.col.category", locale),
                msg("incomeBudget.col.received", locale),
                msg("incomeBudget.col.target",   locale),
                msg("incomeBudget.col.progress", locale),
                msg("incomeBudget.col.status",   locale),
                msg("incomeBudget.col.note",     locale)
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // ----- Data -----
        for (CategoryBudgetDto cat : d.getCategories()) {
            Row r = sheet.createRow(row++);

            Cell catCell = r.createCell(0);
            catCell.setCellValue(cat.getCategory().getName());
            catCell.setCellStyle(bodyStyle);

            Cell receivedCell = r.createCell(1);
            receivedCell.setCellValue(cat.getSpent());
            receivedCell.setCellStyle(moneyStyle);

            Cell targetCell = r.createCell(2);
            if (cat.getLimit() != null && cat.getLimit() > 0) {
                targetCell.setCellValue(cat.getLimit());
                targetCell.setCellStyle(moneyStyle);
            } else {
                targetCell.setCellValue("—");
                targetCell.setCellStyle(bodyStyle);
            }

            Cell pctCell = r.createCell(3);
            pctCell.setCellValue(cat.getPercentUsed() != null ? cat.getPercentUsed() / 100 : 0);
            pctCell.setCellStyle(percentStyle);

            Cell statusCell = r.createCell(4);
            statusCell.setCellValue(localizedIncomeStatus(cat.getStatus(), locale));
            statusCell.setCellStyle(bodyStyle);

            Cell noteCell = r.createCell(5);
            noteCell.setCellValue(cat.getNote() != null ? cat.getNote() : "");
            noteCell.setCellStyle(bodyStyle);
        }

        // ----- Footer -----
        row++;
        Row footerRow = sheet.createRow(row);
        Cell footerCell = footerRow.createCell(0);
        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        footerCell.setCellValue(msg("budget.export.footer", locale, appUrl, timestamp));
        footerCell.setCellStyle(footerStyle);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 5));

        // ----- Column widths -----
        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 15 * 256);
        sheet.setColumnWidth(2, 15 * 256);
        sheet.setColumnWidth(3, 10 * 256);
        sheet.setColumnWidth(4, 15 * 256);
        sheet.setColumnWidth(5, 40 * 256);
    }

    /**
     * Локалізація статусу для income-дашборду.
     * ACHIEVED — ціль досягнута, ON_TRACK — на шляху, BEHIND — відстає, NO_TARGET — без цілі.
     */
    private String localizedIncomeStatus(String status, Locale locale) {
        if (status == null) return "";
        return switch (status) {
            case "ACHIEVED"  -> msg("incomeBudget.status.achieved", locale);
            case "ON_TRACK"  -> msg("incomeBudget.status.onTrack", locale);
            case "BEHIND"    -> msg("incomeBudget.status.behind", locale);
            case "NO_TARGET" -> msg("incomeBudget.status.noTarget", locale);
            default -> status;
        };
    }
}