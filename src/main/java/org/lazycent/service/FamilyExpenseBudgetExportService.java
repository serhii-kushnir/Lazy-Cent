package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.lazycent.model.dto.FamilyExpenseCategoryDto;
import org.lazycent.model.dto.FamilyExpenseDashboardDto;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FamilyExpenseBudgetExportService {

    private final MessageSource messageSource;

    public byte[] export(FamilyExpenseDashboardDto dashboard, int month, int year, String currency, Locale locale)
            throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(msg("family.shared.budget.heading", locale));
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue(msg("family.shared.budget.heading", locale) + " "
                    + YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("MM.yyyy")));
            title.getCell(0).setCellStyle(titleStyle);
            writeMoneyRow(sheet.createRow(1), msg("budget.totalBudget", locale), dashboard.totalBudget(), currency, moneyStyle);
            writeMoneyRow(sheet.createRow(2), msg("budget.spent", locale), dashboard.totalExpenses(), currency, moneyStyle);

            String[] headers = {
                    msg("budget.col.category", locale), msg("budget.col.spent", locale),
                    msg("budget.col.limit", locale), msg("budget.col.progress", locale),
                    msg("budget.col.note", locale)
            };
            Row header = sheet.createRow(4);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowIndex = 5;
            for (FamilyExpenseCategoryDto category : dashboard.categories()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(category.categoryName());
                writeCell(row, 1, category.spent(), moneyStyle);
                writeCell(row, 2, category.limit(), moneyStyle);
                row.createCell(3).setCellValue(category.percentUsed());
                row.createCell(4).setCellValue(category.note() == null ? "" : category.note());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void writeMoneyRow(Row row, String label, double amount, String currency, CellStyle style) {
        row.createCell(0).setCellValue(label);
        writeCell(row, 1, amount, style);
        row.createCell(2).setCellValue(currency);
    }

    private static void writeCell(Row row, int column, double value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }
}
