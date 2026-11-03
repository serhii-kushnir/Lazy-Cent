package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.lazycent.model.Category;
import org.lazycent.model.TotalBudget;
import org.lazycent.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TotalsExportService {

    private final BudgetService budgetService;
    private final MessageSource messageSource;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] exportTotals(User user, LocalDate startDate, LocalDate endDate, Locale locale) throws IOException {
        TotalBudget total = budgetService.getTotalBudget(startDate, endDate, user);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            buildSheet(wb, total, startDate, endDate, locale);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void buildSheet(XSSFWorkbook wb, TotalBudget total, LocalDate startDate, LocalDate endDate, Locale locale) {
        Sheet sheet = wb.createSheet(msg("totals.title", locale));

        CellStyle titleStyle = createTitleStyle(wb);
        CellStyle labelStyle = createLabelStyle(wb);
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle bodyStyle = createBodyStyle(wb);
        CellStyle moneyStyle = createMoneyStyle(wb);
        CellStyle boldMoneyStyle = createBoldMoneyStyle(wb);
        CellStyle percentStyle = createPercentStyle(wb);
        CellStyle footerStyle = createFooterStyle(wb);

        int row = 0;

        Row titleRow = sheet.createRow(row++);
        String period = DATE_FMT.format(startDate) + " — " + DATE_FMT.format(endDate);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(msg("totals.heading", locale) + " (" + period + ")");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        row++;

        row = addSummaryRow(sheet, row, msg("totals.netBalance", locale), total.getBalance(), labelStyle, boldMoneyStyle);
        row = addSummaryRow(sheet, row, msg("totals.income", locale), total.getTotalIncomes(), labelStyle, boldMoneyStyle);
        row = addSummaryRow(sheet, row, msg("totals.expense", locale), total.getTotalExpenses(), labelStyle, boldMoneyStyle);

        row++;

        Row headerRow = sheet.createRow(row++);
        String[] headers = {
                msg("totals.col.number", locale),
                msg("totals.col.category", locale),
                msg("totals.col.type", locale),
                msg("totals.col.amount", locale),
                msg("totals.col.percent", locale)
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        List<CatRow> rows = new ArrayList<>();
        double totalExp = total.getTotalExpenses() != null ? total.getTotalExpenses() : 0.0;
        double totalInc = total.getTotalIncomes() != null ? total.getTotalIncomes() : 0.0;
        if (total.getExpensesByCategory() != null) {
            for (Map.Entry<Category, Double> e : total.getExpensesByCategory().entrySet()) {
                rows.add(new CatRow(e.getKey().getName(), "EXPENSE", e.getValue(),
                        totalExp > 0 ? e.getValue() / totalExp * 100.0 : 0.0));
            }
        }
        if (total.getIncomesByCategory() != null) {
            for (Map.Entry<Category, Double> e : total.getIncomesByCategory().entrySet()) {
                rows.add(new CatRow(e.getKey().getName(), "INCOME", e.getValue(),
                        totalInc > 0 ? e.getValue() / totalInc * 100.0 : 0.0));
            }
        }

        int idx = 1;
        for (CatRow r : rows) {
            Row row2 = sheet.createRow(row++);
            Cell c0 = row2.createCell(0); c0.setCellValue(idx++); c0.setCellStyle(bodyStyle);
            Cell c1 = row2.createCell(1); c1.setCellValue(r.name); c1.setCellStyle(bodyStyle);
            Cell c2 = row2.createCell(2);
            c2.setCellValue("EXPENSE".equals(r.type)
                    ? msg("type.EXPENSE", locale) : msg("type.INCOME", locale));
            c2.setCellStyle(bodyStyle);
            Cell c3 = row2.createCell(3); c3.setCellValue(r.amount); c3.setCellStyle(moneyStyle);
            Cell c4 = row2.createCell(4); c4.setCellValue(r.percent / 100.0); c4.setCellStyle(percentStyle);
        }

        row++;
        Row footerRow = sheet.createRow(row);
        Cell footerCell = footerRow.createCell(0);
        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        footerCell.setCellValue(msg("budget.export.footer", locale, appUrl, timestamp));
        footerCell.setCellStyle(footerStyle);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 4));

        sheet.setColumnWidth(0, 8 * 256);
        sheet.setColumnWidth(1, 30 * 256);
        sheet.setColumnWidth(2, 15 * 256);
        sheet.setColumnWidth(3, 15 * 256);
        sheet.setColumnWidth(4, 10 * 256);
    }

    private record CatRow(String name, String type, Double amount, double percent) {}

    private int addSummaryRow(Sheet sheet, int rowNum, String label, Double value,
                              CellStyle labelStyle, CellStyle moneyStyle) {
        Row r = sheet.createRow(rowNum);
        Cell l = r.createCell(0); l.setCellValue(label); l.setCellStyle(labelStyle);
        Cell v = r.createCell(1);
        if (value != null) v.setCellValue(value);
        v.setCellStyle(moneyStyle);
        return rowNum + 1;
    }

    // ---- Styles ----
    private CellStyle createTitleStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true); f.setFontHeightInPoints((short) 16);
        s.setFont(f);
        return s;
    }
    private CellStyle createLabelStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFont(wb.createFont());
        return s;
    }
    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true); f.setColor(IndexedColors.WHITE.getIndex());
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
        Font f = wb.createFont(); f.setBold(true);
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
}