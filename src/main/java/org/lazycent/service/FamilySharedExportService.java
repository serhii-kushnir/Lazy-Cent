package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.lazycent.model.dto.SharedDayDto;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FamilySharedExportService {

    private final MessageSource messageSource;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] export(List<SharedDayDto> days, LocalDate startDate, LocalDate endDate, Locale locale) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(msg("family.shared.title", locale));

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
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle bodyStyle = workbook.createCellStyle();
            bodyStyle.setBorderBottom(BorderStyle.THIN);
            bodyStyle.setBorderLeft(BorderStyle.HAIR);
            bodyStyle.setBorderRight(BorderStyle.HAIR);
            bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.cloneStyleFrom(bodyStyle);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.cloneStyleFrom(bodyStyle);
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            moneyStyle.setAlignment(HorizontalAlignment.RIGHT);

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue(msg("family.shared.title", locale) + " "
                    + startDate.format(DATE_FORMAT) + " - " + endDate.format(DATE_FORMAT));
            title.getCell(0).setCellStyle(titleStyle);

            Row header = sheet.createRow(2);
            String[] headers = {
                    msg("family.shared.col.date", locale),
                    msg("family.shared.col.day", locale),
                    msg("family.shared.col.expenses", locale),
                    msg("family.shared.col.incomes", locale),
                    msg("family.shared.col.balance", locale),
                    msg("family.shared.col.count", locale)
            };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 3;
            for (SharedDayDto day : days) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(day.date().format(DATE_FORMAT));
                row.createCell(1).setCellValue(day.dayName());
                row.createCell(2).setCellValue(day.totalExpenses());
                row.createCell(3).setCellValue(day.totalIncomes());
                row.createCell(4).setCellValue(day.balance());
                row.createCell(5).setCellValue(day.transactionsCount());

                row.getCell(0).setCellStyle(centerStyle);
                row.getCell(1).setCellStyle(bodyStyle);
                row.getCell(2).setCellStyle(moneyStyle);
                row.getCell(3).setCellStyle(moneyStyle);
                row.getCell(4).setCellStyle(moneyStyle);
                row.getCell(5).setCellStyle(centerStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.setColumnWidth(1, 18 * 256);

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, key, locale);
    }
}
