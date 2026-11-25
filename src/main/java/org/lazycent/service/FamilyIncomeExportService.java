package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.lazycent.model.dto.FamilyIncomeCategoryDto;
import org.lazycent.model.dto.FamilyIncomeDashboardDto;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FamilyIncomeExportService {

    private final MessageSource messageSource;

    public byte[] export(FamilyIncomeDashboardDto dashboard, int month, int year, String currency, Locale locale)
            throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(msg("family.shared.income.heading", locale));
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
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue(msg("family.shared.income.heading", locale) + " "
                    + YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("MM.yyyy")));
            title.getCell(0).setCellStyle(titleStyle);
            Row total = sheet.createRow(1);
            total.createCell(0).setCellValue(msg("family.shared.income.totalTarget", locale));
            Cell totalAmount = total.createCell(1);
            totalAmount.setCellValue(dashboard.totalTarget());
            totalAmount.setCellStyle(moneyStyle);
            total.createCell(2).setCellValue(currency);
            Row received = sheet.createRow(2);
            received.createCell(0).setCellValue(msg("incomeBudget.received", locale));
            Cell receivedAmount = received.createCell(1);
            receivedAmount.setCellValue(dashboard.totalIncomes());
            receivedAmount.setCellStyle(moneyStyle);
            received.createCell(2).setCellValue(currency);

            String[] headers = {
                    msg("incomeBudget.col.category", locale),
                    msg("incomeBudget.col.received", locale),
                    msg("incomeBudget.col.target", locale),
                    msg("incomeBudget.col.progress", locale),
                    msg("incomeBudget.col.note", locale)
            };
            Row header = sheet.createRow(4);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 5;
            for (FamilyIncomeCategoryDto category : dashboard.categories()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(category.categoryName());
                Cell receivedCell = row.createCell(1);
                receivedCell.setCellValue(category.received());
                receivedCell.setCellStyle(moneyStyle);
                Cell targetCell = row.createCell(2);
                targetCell.setCellValue(category.target());
                targetCell.setCellStyle(moneyStyle);
                row.createCell(3).setCellValue(category.percentUsed());
                row.createCell(4).setCellValue(category.note() == null ? "" : category.note());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }
}
