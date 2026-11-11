package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.lazycent.model.User;
import org.lazycent.model.dto.SharedTransactionDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class FamilySharedDayExportService {

    private final MessageSource messageSource;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] export(User user, LocalDate date, String dayName,
                         List<SharedTransactionDto> transactions,
                         double startingBalance, Locale locale) throws IOException {

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet(msg("family.shared.title", locale));

            // ===== Обчислення підсумків =====
            double totalExp = 0, totalInc = 0;
            for (SharedTransactionDto t : transactions) {
                double amt = t.amount() != null ? t.amount() : 0.0;
                if (t.expense()) totalExp += amt;
                else if (t.income()) totalInc += amt;
            }
            double balance = startingBalance + totalInc - totalExp;

            // ===== СПІЛЬНІ СТИЛІ =====
            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setWrapText(true);

            CellStyle statLabelStyle = wb.createCellStyle();
            Font statFont = wb.createFont();
            statFont.setBold(true);
            statLabelStyle.setFont(statFont);
            statLabelStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            statLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            statLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            statLabelStyle.setBorderBottom(BorderStyle.THIN);
            statLabelStyle.setBorderTop(BorderStyle.THIN);
            statLabelStyle.setBorderLeft(BorderStyle.THIN);

            CellStyle statValueStyle = wb.createCellStyle();
            statValueStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            statValueStyle.setFont(statFont);
            statValueStyle.setAlignment(HorizontalAlignment.RIGHT);
            statValueStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            statValueStyle.setBorderBottom(BorderStyle.THIN);
            statValueStyle.setBorderTop(BorderStyle.THIN);
            statValueStyle.setBorderRight(BorderStyle.THIN);

            CellStyle bodyStyle = wb.createCellStyle();
            bodyStyle.setBorderBottom(BorderStyle.THIN);
            bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            bodyStyle.setWrapText(true);

            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.cloneStyleFrom(bodyStyle);
            moneyStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            moneyStyle.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle centerStyle = wb.createCellStyle();
            centerStyle.cloneStyleFrom(bodyStyle);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            int row = 0;

            // ===== TITLE =====
            Row titleRow = sheet.createRow(row++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(msg("family.shared.title", locale) + " — "
                    + (dayName != null ? dayName : "") + ", " + date.format(DATE_FMT));
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));

            row++; // empty

            // ===== STAT BLOCK (після заголовка) =====
            Row statRow1 = sheet.createRow(row);
            Cell s1l = statRow1.createCell(0);
            s1l.setCellValue(msg("day.startingBalance", locale));
            s1l.setCellStyle(statLabelStyle);
            sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 3));
            Cell s1v = statRow1.createCell(4);
            s1v.setCellValue(startingBalance);
            s1v.setCellStyle(statValueStyle);
            sheet.addMergedRegion(new CellRangeAddress(row, row, 4, 6));
            row++;

            Row statRow2 = sheet.createRow(row);
            Cell s2l = statRow2.createCell(0);
            s2l.setCellValue(msg("family.shared.col.incomes", locale));
            s2l.setCellStyle(statLabelStyle);
            sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 3));
            Cell s2v = statRow2.createCell(4);
            s2v.setCellValue(totalInc);
            s2v.setCellStyle(statValueStyle);
            sheet.addMergedRegion(new CellRangeAddress(row, row, 4, 6));
            row++;

            Row statRow3 = sheet.createRow(row);
            Cell s3l = statRow3.createCell(0);
            s3l.setCellValue(msg("family.shared.col.expenses", locale));
            s3l.setCellStyle(statLabelStyle);
            sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 3));
            Cell s3v = statRow3.createCell(4);
            s3v.setCellValue(totalExp);
            s3v.setCellStyle(statValueStyle);
            sheet.addMergedRegion(new CellRangeAddress(row, row, 4, 6));
            row++;

            Row statRow4 = sheet.createRow(row);
            Cell s4l = statRow4.createCell(0);
            s4l.setCellValue(msg("family.shared.col.balance", locale));
            s4l.setCellStyle(statLabelStyle);
            sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 3));
            Cell s4v = statRow4.createCell(4);
            s4v.setCellValue(balance);
            s4v.setCellStyle(statValueStyle);
            sheet.addMergedRegion(new CellRangeAddress(row, row, 4, 6));
            row++;

            row++; // empty

            // ===== HEADER (з №) =====
            String[] headers = {
                    msg("family.shared.export.col.number", locale),
                    msg("common.time", locale),
                    msg("family.shared.col.type", locale),
                    msg("family.shared.col.member", locale),
                    msg("common.description", locale),
                    msg("common.quantity", locale),
                    msg("family.shared.col.amount", locale),
                    msg("common.buyer", locale),
                    msg("common.seller", locale),
                    msg("family.shared.col.category", locale),
                    msg("common.details", locale)
            };
            Row headerRow = sheet.createRow(row++);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // ===== DATA (з порядковим номером) =====
            String typeExp = msg("type.EXPENSE", locale);
            String typeInc = msg("type.INCOME", locale);
            int idx = 1;

            for (SharedTransactionDto t : transactions) {
                Row r = sheet.createRow(row++);

                Cell c0 = r.createCell(0);
                c0.setCellValue(idx++);
                c0.setCellStyle(centerStyle);

                Cell c1 = r.createCell(1);
                c1.setCellValue(t.time() != null ? t.time().toString() : "—");
                c1.setCellStyle(centerStyle);

                Cell c2 = r.createCell(2);
                c2.setCellValue(t.expense() ? typeExp : typeInc);
                c2.setCellStyle(centerStyle);

                Cell c3 = r.createCell(3);
                c3.setCellValue(t.memberUsername() != null ? t.memberUsername() : "");
                c3.setCellStyle(bodyStyle);

                Cell c4 = r.createCell(4);
                c4.setCellValue(t.description() != null ? t.description() : "");
                c4.setCellStyle(bodyStyle);

                Cell c5 = r.createCell(5);
                c5.setCellValue(t.quantity() != null ? t.quantity() : 1);
                c5.setCellStyle(centerStyle);

                Cell c6 = r.createCell(6);
                c6.setCellValue(t.amount() != null ? t.amount() : 0.0);
                c6.setCellStyle(moneyStyle);

                Cell c7 = r.createCell(7);
                c7.setCellValue(t.buyer() != null ? t.buyer() : "");
                c7.setCellStyle(bodyStyle);

                Cell c8 = r.createCell(8);
                c8.setCellValue(t.seller() != null ? t.seller() : "");
                c8.setCellStyle(bodyStyle);

                Cell c9 = r.createCell(9);
                c9.setCellValue(t.categoryName() != null ? t.categoryName() : "");
                c9.setCellStyle(bodyStyle);

                Cell c10 = r.createCell(10);
                c10.setCellValue(t.details() != null ? t.details() : "");
                c10.setCellStyle(bodyStyle);
            }

            // ===== FOOTER =====
            row++;
            CellStyle footerStyle = wb.createCellStyle();
            Font footerFont = wb.createFont();
            footerFont.setFontHeightInPoints((short) 9);
            footerFont.setItalic(true);
            footerFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            footerStyle.setFont(footerFont);
            footerStyle.setBorderTop(BorderStyle.THIN);
            footerStyle.setAlignment(HorizontalAlignment.CENTER);
            footerStyle.setWrapText(true);

            Row footerRow = sheet.createRow(row);
            Cell footerCell = footerRow.createCell(0);
            footerCell.setCellValue(msg("budget.export.footer", locale, appUrl,
                    LocalDateTime.now().format(TS_FORMAT)));
            footerCell.setCellStyle(footerStyle);
            sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 10));

            // ===== WIDTHS =====
            sheet.setColumnWidth(0, 6 * 256);   // №
            sheet.setColumnWidth(1, 10 * 256);  // Час
            sheet.setColumnWidth(2, 12 * 256);  // Тип
            sheet.setColumnWidth(3, 20 * 256);  // Учасник
            sheet.setColumnWidth(4, 35 * 256);  // Найменування
            sheet.setColumnWidth(5, 10 * 256);  // Кількість
            sheet.setColumnWidth(6, 15 * 256);  // Сума
            sheet.setColumnWidth(7, 18 * 256);  // Покупець
            sheet.setColumnWidth(8, 18 * 256);  // Продавець
            sheet.setColumnWidth(9, 20 * 256);  // Категорія
            sheet.setColumnWidth(10, 30 * 256); // Деталі

            wb.write(out);
            return out.toByteArray();
        }
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