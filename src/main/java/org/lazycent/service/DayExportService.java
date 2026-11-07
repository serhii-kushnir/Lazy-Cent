package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.lazycent.model.DayBudget;
import org.lazycent.model.Expense;
import org.lazycent.model.Income;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class DayExportService {

    private final MessageSource messageSource;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] export(DayBudget dayBudget, Locale locale) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            buildSheet(wb, dayBudget, locale);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void buildSheet(XSSFWorkbook wb, DayBudget day, Locale locale) {
        Sheet sheet = wb.createSheet(msg("day.export.title", locale));

        CellStyle titleStyle = createTitleStyle(wb);
        CellStyle labelStyle = createLabelStyle(wb);
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle bodyStyle = createBodyStyle(wb);
        CellStyle moneyStyle = createMoneyStyle(wb);
        CellStyle centerStyle = createCenterStyle(wb);
        CellStyle boldMoneyStyle = createBoldMoneyStyle(wb);
        CellStyle footerStyle = createFooterStyle(wb);

        int row = 0;

        // ===== TITLE =====
        Row titleRow = sheet.createRow(row++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(msg("day.export.heading", locale, day.getDayName(), day.getDate().format(DATE_FMT)));
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        row++; // empty

        // ===== SUMMARY =====
        row = addSummaryRow(sheet, row, msg("day.startingBalance", locale), day.getStartingBalance() != null ? day.getStartingBalance() : 0.0, labelStyle, boldMoneyStyle);
        row = addSummaryRow(sheet, row, msg("day.income", locale), day.getTotalIncomes(), labelStyle, boldMoneyStyle);
        row = addSummaryRow(sheet, row, msg("day.expense", locale), day.getTotalExpenses(), labelStyle, boldMoneyStyle);
        row = addSummaryRow(sheet, row, msg("day.balance", locale), day.getRemainingBalance() != null ? day.getRemainingBalance() : 0.0, labelStyle, boldMoneyStyle);

        row++; // empty

        // ===== HEADER =====
        String[] headers = {
                msg("common.type", locale),
                msg("common.time", locale),
                msg("common.category", locale),
                msg("common.description", locale),
                msg("common.quantity", locale),
                msg("common.amount", locale),
                msg("common.buyer", locale),
                msg("common.seller", locale),
                msg("common.details", locale)
        };
        Row headerRow = sheet.createRow(row++);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // ===== EXPENSES =====
        for (Expense e : day.getExpenses()) {
            Row r = sheet.createRow(row++);
            Cell c0 = r.createCell(0); c0.setCellValue(msg("type.EXPENSE", locale)); c0.setCellStyle(centerStyle);
            Cell c1 = r.createCell(1); c1.setCellValue(e.getTime() != null ? e.getTime().toString() : "—"); c1.setCellStyle(centerStyle);
            Cell c2 = r.createCell(2); c2.setCellValue(e.getCategory().getName()); c2.setCellStyle(bodyStyle);
            Cell c3 = r.createCell(3); c3.setCellValue(e.getDescription()); c3.setCellStyle(bodyStyle);
            Cell c4 = r.createCell(4); c4.setCellValue(e.getQuantity() != null ? e.getQuantity() : 1); c4.setCellStyle(centerStyle);
            Cell c5 = r.createCell(5); c5.setCellValue(e.getAmount() != null ? e.getAmount() : 0.0); c5.setCellStyle(moneyStyle);
            Cell c6 = r.createCell(6); c6.setCellValue(e.getBuyer() != null ? e.getBuyer() : "—"); c6.setCellStyle(bodyStyle);
            Cell c7 = r.createCell(7); c7.setCellValue(e.getSeller() != null ? e.getSeller() : "—"); c7.setCellStyle(bodyStyle);
            Cell c8 = r.createCell(8); c8.setCellValue(e.getDetails() != null ? e.getDetails() : "—"); c8.setCellStyle(bodyStyle);
        }

        // ===== INCOMES =====
        for (Income i : day.getIncomes()) {
            Row r = sheet.createRow(row++);
            Cell c0 = r.createCell(0); c0.setCellValue(msg("type.INCOME", locale)); c0.setCellStyle(centerStyle);
            Cell c1 = r.createCell(1); c1.setCellValue(i.getTime() != null ? i.getTime().toString() : "—"); c1.setCellStyle(centerStyle);
            Cell c2 = r.createCell(2); c2.setCellValue(i.getCategory().getName()); c2.setCellStyle(bodyStyle);
            Cell c3 = r.createCell(3); c3.setCellValue(i.getDescription()); c3.setCellStyle(bodyStyle);
            Cell c4 = r.createCell(4); c4.setCellValue(i.getQuantity() != null ? i.getQuantity() : 1); c4.setCellStyle(centerStyle);
            Cell c5 = r.createCell(5); c5.setCellValue(i.getAmount() != null ? i.getAmount() : 0.0); c5.setCellStyle(moneyStyle);
            Cell c6 = r.createCell(6); c6.setCellValue(i.getBuyer() != null ? i.getBuyer() : "—"); c6.setCellStyle(bodyStyle);
            Cell c7 = r.createCell(7); c7.setCellValue(i.getSeller() != null ? i.getSeller() : "—"); c7.setCellStyle(bodyStyle);
            Cell c8 = r.createCell(8); c8.setCellValue(i.getDetails() != null ? i.getDetails() : "—"); c8.setCellStyle(bodyStyle);
        }

        // ===== FOOTER =====
        row++;
        Row footerRow = sheet.createRow(row);
        Cell footerCell = footerRow.createCell(0);
        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        footerCell.setCellValue(msg("budget.export.footer", locale, appUrl, timestamp));
        footerCell.setCellStyle(footerStyle);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 8));

        // ===== WIDTHS =====
        sheet.setColumnWidth(0, 12 * 256);
        sheet.setColumnWidth(1, 10 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.setColumnWidth(3, 30 * 256);
        sheet.setColumnWidth(4, 10 * 256);
        sheet.setColumnWidth(5, 15 * 256);
        sheet.setColumnWidth(6, 15 * 256);
        sheet.setColumnWidth(7, 15 * 256);
        sheet.setColumnWidth(8, 25 * 256);
    }

    private int addSummaryRow(Sheet sheet, int rowNum, String label, Double value, CellStyle labelStyle, CellStyle moneyStyle) {
        Row r = sheet.createRow(rowNum);
        Cell l = r.createCell(0); l.setCellValue(label); l.setCellStyle(labelStyle);
        Cell v = r.createCell(1); v.setCellValue(value != null ? value : 0.0); v.setCellStyle(moneyStyle);
        return rowNum + 1;
    }

    // Styles
    private CellStyle createTitleStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont(); f.setBold(true); f.setFontHeightInPoints((short) 14);
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
        Font f = wb.createFont(); f.setBold(true); f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f); s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN); s.setAlignment(HorizontalAlignment.CENTER); s.setVerticalAlignment(VerticalAlignment.CENTER); s.setWrapText(true);
        return s;
    }
    private CellStyle createBodyStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setBorderBottom(BorderStyle.THIN); s.setBorderLeft(BorderStyle.HAIR); s.setBorderRight(BorderStyle.HAIR); s.setVerticalAlignment(VerticalAlignment.CENTER); s.setWrapText(true);
        return s;
    }
    private CellStyle createCenterStyle(XSSFWorkbook wb) {
        CellStyle s = createBodyStyle(wb); s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }
    private CellStyle createMoneyStyle(XSSFWorkbook wb) {
        CellStyle s = createBodyStyle(wb);
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00")); s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }
    private CellStyle createBoldMoneyStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont(); f.setBold(true);
        s.setFont(f); s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00")); s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }
    private CellStyle createFooterStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont(); f.setFontHeightInPoints((short) 9); f.setItalic(true); f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFont(f); s.setBorderTop(BorderStyle.THIN); s.setAlignment(HorizontalAlignment.CENTER); s.setWrapText(true);
        return s;
    }

    private String msg(String key, Locale locale, Object... args) {
        try { return messageSource.getMessage(key, args, locale); }
        catch (Exception e) { return key; }
    }
}