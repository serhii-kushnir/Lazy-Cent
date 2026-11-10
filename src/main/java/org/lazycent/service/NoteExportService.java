package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteExportService {

    private final NoteService noteService;
    private final MessageSource messageSource;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static final String FILTER_ALL      = "all";
    public static final String FILTER_ACTIVE   = "active";
    public static final String FILTER_ARCHIVED = "archived";
    public static final String FILTER_SHARED   = "shared";

    public byte[] export(User user, String sortKey, String filterKey, Locale locale)
            throws IOException {

        Sort sort = NoteService.resolveSort(sortKey, false);

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            String key = normalizeFilter(filterKey);

            switch (key) {
                case FILTER_ACTIVE -> {
                    List<Note> active = noteService.getAllActiveNotes(user, sort);
                    if (!active.isEmpty()) {
                        buildSheet(wb, active, "active", locale);
                    }
                }
                case FILTER_ARCHIVED -> {
                    List<Note> archived = noteService.getAllArchivedNotes(
                            user, NoteService.resolveSort("default", true));
                    if (!archived.isEmpty()) {
                        buildSheet(wb, archived, "archived", locale);
                    }
                }
                case FILTER_SHARED -> {
                    // FIX: Беремо нотатки сім'ї, а не власні
                    List<Note> shared = noteService.getFamilySharedNotes(user);
                    if (!shared.isEmpty()) {
                        buildSheet(wb, shared, "shared", locale);
                    }
                }
                case FILTER_ALL -> {
                    // Три окремі аркуші
                    List<Note> active = noteService.getAllActiveNotes(user, sort);
                    if (!active.isEmpty()) {
                        buildSheet(wb, active, "active", locale);
                    }
                    List<Note> archived = noteService.getAllArchivedNotes(
                            user, NoteService.resolveSort("default", true));
                    if (!archived.isEmpty()) {
                        buildSheet(wb, archived, "archived", locale);
                    }
                    // FIX: Беремо спільні нотатки сім'ї
                    List<Note> shared = noteService.getFamilySharedNotes(user);
                    if (!shared.isEmpty()) {
                        buildSheet(wb, shared, "shared", locale);
                    }
                }
                default -> { /* нічого */ }
            }

            // Якщо жодного аркуша не створено (усі списки порожні) — додаємо порожній
            if (wb.getNumberOfSheets() == 0) {
                buildEmptySheet(wb, key, locale);
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void buildSheet(XSSFWorkbook wb, List<Note> notes,
                            String sheetType, Locale locale) {

        String sheetName = msg("notes.export.sheet." + sheetType, locale);
        Sheet sheet = wb.createSheet(sheetName);

        CellStyle titleStyle   = createTitleStyle(wb);
        CellStyle headerStyle  = createHeaderStyle(wb);
        CellStyle bodyStyle    = createBodyStyle(wb);
        CellStyle dateStyle    = createDateStyle(wb);
        CellStyle footerStyle  = createFooterStyle(wb);
        CellStyle centerStyle  = createCenterStyle(wb);

        int row = 0;

        // ----- Title -----
        Row titleRow = sheet.createRow(row++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(
                msg("notes.export.heading", locale, sheetName, notes.size()));
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

        row++; // empty

        // ----- Header -----
        String[] headers = {
                msg("notes.export.col.number", locale),
                msg("notes.export.col.title", locale),
                msg("notes.export.col.content", locale),
                msg("notes.export.col.bgColor", locale),
                msg("notes.export.col.textColor", locale),
                msg("notes.export.col.pinned", locale),
                msg("notes.export.col.shared", locale),
                msg("notes.export.col.created", locale),
                msg("notes.export.col.updated", locale)
        };
        Row headerRow = sheet.createRow(row++);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // ----- Data -----
        String yes = msg("common.yes", locale);
        String no  = msg("common.no", locale);

        int idx = 1;
        for (Note n : notes) {
            Row r = sheet.createRow(row++);

            Cell c0 = r.createCell(0);
            c0.setCellValue(idx++);
            c0.setCellStyle(centerStyle);

            Cell c1 = r.createCell(1);
            c1.setCellValue(n.getTitle() != null ? n.getTitle() : "");
            c1.setCellStyle(bodyStyle);

            Cell c2 = r.createCell(2);
            c2.setCellValue(n.getContent() != null ? n.getContent() : "");
            c2.setCellStyle(bodyStyle);

            Cell c3 = r.createCell(3);
            c3.setCellValue(n.getColor() != null ? n.getColor() : "");
            c3.setCellStyle(bodyStyle);

            Cell c4 = r.createCell(4);
            c4.setCellValue(n.getTextColor() != null ? n.getTextColor() : "");
            c4.setCellStyle(bodyStyle);

            Cell c5 = r.createCell(5);
            c5.setCellValue(Boolean.TRUE.equals(n.isPinned()) ? yes : no);
            c5.setCellStyle(centerStyle);

            Cell c6 = r.createCell(6);
            c6.setCellValue(Boolean.TRUE.equals(n.getShareWithFamily()) ? yes : no);
            c6.setCellStyle(centerStyle);

            Cell c7 = r.createCell(7);
            c7.setCellValue(n.getCreatedAt() != null
                    ? n.getCreatedAt().format(DATE_FORMAT) : "");
            c7.setCellStyle(dateStyle);

            Cell c8 = r.createCell(8);
            c8.setCellValue(n.getUpdatedAt() != null
                    ? n.getUpdatedAt().format(DATE_FORMAT) : "");
            c8.setCellStyle(dateStyle);
        }

        // ----- Footer -----
        row++;
        Row footerRow = sheet.createRow(row);
        Cell footerCell = footerRow.createCell(0);
        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        footerCell.setCellValue(msg("notes.export.footer", locale, appUrl, timestamp));
        footerCell.setCellStyle(footerStyle);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 8));

        // ----- Column widths -----
        sheet.setColumnWidth(0, 6 * 256);
        sheet.setColumnWidth(1, 30 * 256);
        sheet.setColumnWidth(2, 60 * 256);
        sheet.setColumnWidth(3, 14 * 256);
        sheet.setColumnWidth(4, 14 * 256);
        sheet.setColumnWidth(5, 12 * 256);
        sheet.setColumnWidth(6, 12 * 256);
        sheet.setColumnWidth(7, 18 * 256);
        sheet.setColumnWidth(8, 18 * 256);

        sheet.createFreezePane(0, 3);
    }

    private void buildEmptySheet(XSSFWorkbook wb, String filterKey, Locale locale) {
        String sheetName = msg("notes.export.sheet.empty", locale);
        Sheet sheet = wb.createSheet(sheetName);

        CellStyle titleStyle = createTitleStyle(wb);
        Row r = sheet.createRow(0);
        Cell c = r.createCell(0);
        c.setCellValue(msg("notes.export.empty", locale,
                msg("notes.export.filter." + normalizeFilter(filterKey), locale)));
        c.setCellStyle(titleStyle);
        sheet.setColumnWidth(0, 80 * 256);
    }

    private String normalizeFilter(String filterKey) {
        if (filterKey == null) return FILTER_ALL;
        String key = filterKey.toLowerCase();
        if (FILTER_ACTIVE.equals(key)
                || FILTER_ARCHIVED.equals(key)
                || FILTER_SHARED.equals(key)) {
            return key;
        }
        return FILTER_ALL;
    }

    // ==================== Styles ====================

    private CellStyle createTitleStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
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
        s.setVerticalAlignment(VerticalAlignment.TOP);
        s.setWrapText(true);
        return s;
    }

    private CellStyle createCenterStyle(XSSFWorkbook wb) {
        CellStyle s = createBodyStyle(wb);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private CellStyle createDateStyle(XSSFWorkbook wb) {
        CellStyle s = createBodyStyle(wb);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
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