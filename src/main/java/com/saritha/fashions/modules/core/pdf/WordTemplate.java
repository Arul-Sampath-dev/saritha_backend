package com.saritha.fashions.modules.core.pdf;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the Word templates in src/main/resources (Invoice.docx, Receipt.docx, PaymentReceipt.docx).
 *
 * The templates are designed in Word and carry {{name}} placeholders; the formatting of the text
 * around a placeholder (font, bold, alignment, cell borders) is kept.
 * - Repeating rows: a table row with {{prefix.field}} placeholders (e.g. {{item.name}}) is copied
 *   once per entry of {@code rows.get("item")}, then removed. Blank rows right after it are filler
 *   (the invoice shows at least {@code minRows} lines): one blank row is dropped per extra entry.
 * - Optional rows: a row with a placeholder listed in {@code dropRowsWith} is removed
 *   (e.g. the IGST row of a same-state bill).
 * - A placeholder without a value prints "-".
 */
final class WordTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_.]+)}}");
    private static final String MISSING = "-";

    private WordTemplate() {
    }

    /** One repeating block: its placeholder prefix ("item"), entries and the minimum number of lines shown. */
    record Rows(String prefix, List<Map<String, String>> entries, int minRows) {
    }

    static byte[] fill(String templateName, Map<String, String> values, List<Rows> repeats, Set<String> dropRowsWith) {
        try (InputStream template = new ClassPathResource(templateName).getInputStream();
             XWPFDocument document = new XWPFDocument(template)) {
            // 1) structure: repeat rows (each copy filled at once), drop optional rows
            for (XWPFTable table : document.getTables()) {
                for (Rows rows : repeats) {
                    repeat(table, rows, values);
                }
                dropRows(table, dropRowsWith);
            }
            // 2) reload (rows copied by POI are detached from the document) and fill the rest
            try (XWPFDocument filled = new XWPFDocument(new ByteArrayInputStream(write(document)))) {
                for (XWPFParagraph paragraph : filled.getParagraphs()) {
                    replace(paragraph, values);
                }
                for (XWPFTable table : filled.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                                replace(paragraph, values);
                            }
                        }
                    }
                }
                return write(filled);
            }
        } catch (IOException exception) {
            throw new PdfGenerationException("Could not fill template " + templateName, exception);
        }
    }

    // ------------------------------------------------------------------ rows

    private static void repeat(XWPFTable table, Rows rows, Map<String, String> values) {
        String marker = "{{" + rows.prefix() + ".";
        List<XWPFTableRow> tableRows = table.getRows();
        int index = -1;
        for (int i = 0; i < tableRows.size(); i++) {
            if (text(tableRows.get(i)).contains(marker)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        XWPFTableRow template = table.getRow(index);

        // filler: blank rows right after the template row
        int fillers = 0;
        while (index + 1 + fillers < table.getRows().size() && text(table.getRow(index + 1 + fillers)).isBlank()) {
            fillers++;
        }
        // keep enough blank lines to show at least minRows lines in total
        int keepFillers = Math.min(fillers, Math.max(0, rows.minRows() - rows.entries().size()));
        for (int i = fillers; i > keepFillers; i--) {
            table.removeRow(index + i);
        }

        // copies of the template row, filled, inserted after it
        int position = index + 1;
        for (Map<String, String> entry : rows.entries()) {
            CTRow copy = (CTRow) template.getCtRow().copy();
            XWPFTableRow row = new XWPFTableRow(copy, table);
            Map<String, String> rowValues = new HashMap<>(values);
            entry.forEach((key, value) -> rowValues.put(rows.prefix() + "." + key, value));
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    replace(paragraph, rowValues);
                }
            }
            table.addRow(row, position++);
        }
        table.removeRow(index);
    }

    private static void dropRows(XWPFTable table, Set<String> dropRowsWith) {
        if (dropRowsWith.isEmpty()) {
            return;
        }
        for (int i = table.getRows().size() - 1; i >= 0; i--) {
            Matcher matcher = PLACEHOLDER.matcher(text(table.getRow(i)));
            while (matcher.find()) {
                if (dropRowsWith.contains(matcher.group(1))) {
                    table.removeRow(i);
                    break;
                }
            }
        }
    }

    private static String text(XWPFTableRow row) {
        StringBuilder text = new StringBuilder();
        for (XWPFTableCell cell : row.getTableCells()) {
            text.append(cell.getText()).append(' ');
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ text

    /**
     * Replaces the placeholders of a paragraph. Word may split a placeholder over several runs, so the
     * paragraph text is rebuilt in its first run (keeping that run's formatting) and the other runs removed.
     */
    private static void replace(XWPFParagraph paragraph, Map<String, String> values) {
        String text = paragraph.getText();
        if (text == null || !text.contains("{{")) {
            return;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null || value.isBlank() ? MISSING : value));
        }
        matcher.appendTail(out);

        List<XWPFRun> runs = new ArrayList<>(paragraph.getRuns());
        if (runs.isEmpty()) {
            return;
        }
        XWPFRun first = runs.get(0);
        for (int i = runs.size() - 1; i > 0; i--) {
            paragraph.removeRun(i);
        }
        // clear every text element of the first run, then set the new text
        int texts = first.getCTR().sizeOfTArray();
        for (int i = texts - 1; i > 0; i--) {
            first.getCTR().removeT(i);
        }
        first.setText(out.toString(), 0);
    }

    private static byte[] write(XWPFDocument document) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.write(out);
            return out.toByteArray();
        }
    }
}
