package com.saritha.fashions.modules.core.pdf;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHeight;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Fills the Word templates for invoices and receipts. A faithful port of the template code in the
 * legacy ReceiptServiceImpl (replaceText, replaceItemList, addFooterRows, ...): same templates,
 * same rows, labels, column widths, row heights and number formatting (String.valueOf(double),
 * e.g. "1850.0"), so the PDFs look the same.
 *
 * Fixed vs the legacy code (these only change output where the legacy code crashed or printed
 * "null"):
 * - a missing placeholder value (e.g. a shop without GST number) is left blank; the legacy code
 *   crashed with a NullPointerException in String.replace;
 * - a run without text is read as "", not as the word "null";
 * - missing payment method / status / delivery date / HSN print as "-", not "null";
 * - the filled document is kept in memory (the legacy code left a temp file behind per PDF).
 */
final class SalesDocumentTemplate {

    /** The legacy file names are swapped: invoices use Receipt_SF.docx, receipts Invoice_SF.docx. */
    static final String INVOICE_TEMPLATE = "Receipt_SF.docx";
    static final String RECEIPT_TEMPLATE = "Invoice_SF.docx";

    private static final String MISSING = "-";

    private SalesDocumentTemplate() {
    }

    /** One item row. */
    record Line(String itemName, double quantity, double price) {
    }

    /** The footer values (same set as the legacy ReceiptAndInvoicePDF). */
    record Totals(double totalAmount, double discount, double taxableAmount,
                  double cgstRate, double sgstRate, double igstRate,
                  double cgstAmount, double sgstAmount, double igstAmount,
                  double totalAmountWithGST, double advancePaid, double balanceDue,
                  String paymentMethod, String paymentStatus, String deliveryDate, String hsnCode) {
    }

    /** Fills the template and returns the .docx bytes. */
    static byte[] fill(String templateName, Map<String, String> placeholders, List<Line> lines, Totals totals) {
        try (InputStream template = new ClassPathResource(templateName).getInputStream();
             XWPFDocument document = new XWPFDocument(template);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                replaceText(paragraph, placeholders);
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            replaceText(paragraph, placeholders);
                        }
                    }
                }
            }
            replaceItemList(document, lines, totals);

            document.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new PdfGenerationException("Could not fill template " + templateName, exception);
        }
    }

    // ------------------------------------------------------------------ ported legacy logic

    private static void replaceText(XWPFParagraph paragraph, Map<String, String> values) {
        List<XWPFRun> runs = paragraph.getRuns();
        StringBuilder paragraphText = new StringBuilder();
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            paragraphText.append(text == null ? "" : text);
        }

        String text = paragraphText.toString();
        boolean replaced = false;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            if (text.contains(placeholder)) {
                text = text.replace(placeholder, entry.getValue() == null ? "" : entry.getValue());
                replaced = true;
            }
        }

        if (replaced) {
            for (int i = runs.size() - 1; i >= 0; i--) {
                paragraph.removeRun(i);
            }
            paragraph.createRun().setText(text);
        }
    }

    private static void replaceItemList(XWPFDocument document, List<Line> lines, Totals totals) {
        for (XWPFTable table : document.getTables()) {
            if (table.getRows().size() > 1) {
                // Clear the sample item rows, keeping the header (and a "total" row if present)
                int lastRowIndex = table.getRows().size() - 1;
                XWPFTableRow lastRow = table.getRow(lastRowIndex);
                boolean hasTotalRow = lastRow.getCell(0).getText().toLowerCase().contains("total");
                while (table.getRows().size() > (hasTotalRow ? 2 : 1)) {
                    table.removeRow(1);
                }

                for (Line line : lines) {
                    XWPFTableRow newRow = table.createRow();
                    while (newRow.getTableCells().size() < 3) {
                        newRow.addNewTableCell();
                    }
                    newRow.getCell(0).setText(line.itemName() != null ? line.itemName() : "N/A");
                    newRow.getCell(1).setText(quantityText(line.quantity()));
                    newRow.getCell(2).setText(String.valueOf(line.price()));
                    setColumnWidth(newRow, 0, 3000, 500);
                    setColumnWidth(newRow, 1, 1500, 500);
                    setColumnWidth(newRow, 2, 2000, 500);
                }

                addOneRowGap(table);
                addFooterRows(table, totals);
            }
        }
    }

    private static void addFooterRows(XWPFTable table, Totals totals) {
        footerRow(table, "Total Amount:", String.valueOf(totals.totalAmount()));
        footerRow(table, "Discount:", String.valueOf(totals.discount()));
        footerRow(table, "Taxable Amount:", String.valueOf(totals.taxableAmount()));
        if (totals.igstRate() > 0) {
            footerRow(table, "IGST (" + totals.igstRate() + "%):", String.valueOf(totals.igstAmount()));
        } else {
            footerRow(table, "CGST (" + totals.cgstRate() + "%):", String.valueOf(totals.cgstAmount()));
            footerRow(table, "SGST (" + totals.sgstRate() + "%):", String.valueOf(totals.sgstAmount()));
        }
        footerRow(table, "Total Amount (Incl. GST):", String.valueOf(totals.totalAmountWithGST()));
        addOneRowGap(table);
        footerRow(table, "Advance Paid:", String.valueOf(totals.advancePaid()));
        footerRow(table, "Balance Due:", String.valueOf(totals.balanceDue()));
        addOneRowGap(table);
        footerRow(table, "Payment Method:", textOrMissing(totals.paymentMethod()));
        footerRow(table, "Payment Status:", textOrMissing(totals.paymentStatus()));
        addOneRowGap(table);
        footerRow(table, "Expected Delivery Date:", textOrMissing(totals.deliveryDate()));
        footerRow(table, "HSN Code:", textOrMissing(totals.hsnCode()));
    }

    /** Label in column 0, value in column 3, widths 4000 / 3000, height 500 (as before). */
    private static void footerRow(XWPFTable table, String label, String value) {
        XWPFTableRow row = table.createRow();
        while (row.getTableCells().size() < 4) {
            row.addNewTableCell();
        }
        row.getCell(0).setText(label);
        row.getCell(3).setText(value);
        setColumnWidth(row, 0, 4000, 500);
        setColumnWidth(row, 3, 3000, 500);
    }

    private static void addOneRowGap(XWPFTable table) {
        XWPFTableRow emptyRow = table.createRow();
        emptyRow.getCell(0).setText("");
        emptyRow.getCell(1).setText("");
        emptyRow.getCell(2).setText("");
        setRowHeight(emptyRow, 800);
    }

    private static void setColumnWidth(XWPFTableRow row, int cellIndex, int width, int height) {
        if (row.getCell(cellIndex) != null) {
            CTTcPr tcPr = row.getCell(cellIndex).getCTTc().addNewTcPr();
            CTTblWidth cellWidth = tcPr.addNewTcW();
            cellWidth.setType(STTblWidth.DXA);
            cellWidth.setW(BigInteger.valueOf(width));
        }
        setRowHeight(row, height);
    }

    private static void setRowHeight(XWPFTableRow row, int height) {
        CTRow ctRow = row.getCtRow();
        CTTrPr trPr = ctRow.isSetTrPr() ? ctRow.getTrPr() : ctRow.addNewTrPr();
        CTHeight trHeight = trPr.addNewTrHeight();
        trHeight.setVal(BigInteger.valueOf(height));
    }

    /** Whole quantities print as before ("2"); decimals as "2.5" (no trailing zeros). */
    static String quantityText(double quantity) {
        return java.math.BigDecimal.valueOf(quantity).stripTrailingZeros().toPlainString();
    }

    private static String textOrMissing(String value) {
        return value == null || value.isBlank() ? MISSING : value;
    }
}
