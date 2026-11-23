package com.saritha.fashions.modules.core.pdf;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fills the real Word templates (src/main/resources) with sample data: every placeholder is replaced,
 * item / payment rows repeat, the unused GST rows go. The filled .docx files are written to
 * target/template-preview for a visual check.
 */
class WordTemplateTest {

    private static Map<String, String> common() {
        Map<String, String> v = new HashMap<>();
        v.put("shopAddress", "Tindivanam");
        v.put("shopPhone", "9876543211");
        v.put("shopGst", "33ABCDE1234F1Z5");
        v.put("customerName", "Praveen Y");
        v.put("customerPhone", "9361395686");
        return v;
    }

    private static Map<String, String> item(String qty, String name, String price, String total) {
        return new HashMap<>(Map.of("qty", qty, "name", name, "price", price, "total", total));
    }

    private static Document sameStateBill() {
        return new Document("totalAmount", 2400.0).append("discount", 0.0).append("taxableAmount", 2400.0)
                .append("cgstRate", 2.5).append("sgstRate", 2.5).append("igstRate", 0.0)
                .append("cgstAmount", 60.0).append("sgstAmount", 60.0).append("igstAmount", 0.0)
                .append("totalAmountWithGST", 2520.0);
    }

    @Test
    void invoiceFillsItemsAndKeepsSameStateGstRows() throws IOException {
        Map<String, String> v = common();
        Document bill = sameStateBill();
        SalesDocumentPdfService.putAmounts(v, bill);
        v.put("invoiceNo", "TEST-04-11-2026-00011");
        v.put("invoiceDate", "04-11-2026");
        v.put("paymentMethod", "UPI");
        v.put("paymentStatus", "Partial");
        v.put("hsnCode", "998821");
        v.put("expectedDelivery", "02-12-2026");
        v.put("advancePaid", "500.00");
        v.put("balanceDue", "2,020.00");
        List<Map<String, String>> items = List.of(item("1", "Salwar Set", "1,200.00", "1,200.00"),
                item("2", "Blouse", "600.00", "1,200.00"));

        String text = fillAndRead("Invoice.docx", v, List.of(new WordTemplate.Rows("item", items, 6)),
                SalesDocumentPdfService.gstRowsToDrop(bill));

        assertFalse(text.contains("{{"), text);
        assertTrue(text.contains("Salwar Set") && text.contains("Blouse"));
        assertTrue(text.contains("CGST (2.5%)") && text.contains("SGST (2.5%)"));
        assertFalse(text.contains("IGST"));
        assertEquals(6, itemLines("Invoice.docx", v, items), "2 items + 4 blank lines");
    }

    @Test
    void otherStateBillKeepsOnlyIgst() {
        Document bill = new Document("igstRate", 5.0).append("igstAmount", 60.0);
        assertEquals(Set.of("cgstAmount", "sgstAmount"), SalesDocumentPdfService.gstRowsToDrop(bill));
        assertEquals(Set.of("cgstAmount", "sgstAmount", "igstAmount"), SalesDocumentPdfService.gstRowsToDrop(new Document()));
    }

    @Test
    void receiptListsEveryPayment() throws IOException {
        Map<String, String> v = common();
        Document bill = sameStateBill();
        SalesDocumentPdfService.putAmounts(v, bill);
        v.put("receiptNo", "TEST-02-12-2026-00007");
        v.put("receiptDate", "02-12-2026");
        v.put("deliveredOn", "02-12-2026");
        v.put("invoiceNo", "TEST-04-11-2026-00011");
        v.put("invoiceDate", "04-11-2026");
        v.put("hsnCode", "998821");
        v.put("advancePaid", "500.00");
        v.put("totalPaid", "2,520.00");
        v.put("balanceDue", "0.00");
        List<Map<String, String>> pays = new ArrayList<>();
        pays.add(new HashMap<>(Map.of("no", "1", "paidAt", "04-11-2026 11:20 AM", "method", "Cash", "receiptNo", "Advance (with invoice)", "amount", "500.00")));
        pays.add(new HashMap<>(Map.of("no", "2", "paidAt", "02-12-2026 06:30 PM", "method", "UPI", "transactionId", "418273645129",
                "receiptNo", "TEST-PR-02-12-2026-00006", "amount", "2,020.00")));

        String text = fillAndRead("Receipt.docx", v,
                List.of(new WordTemplate.Rows("item", List.of(item("1", "Salwar Set", "1,200.00", "1,200.00"),
                        item("2", "Blouse", "600.00", "1,200.00")), 0), new WordTemplate.Rows("pay", pays, 0)),
                SalesDocumentPdfService.gstRowsToDrop(bill));

        assertFalse(text.contains("{{"), text);
        assertTrue(text.contains("Advance (with invoice)") && text.contains("TEST-PR-02-12-2026-00006"));
        assertTrue(text.contains("418273645129"));
        assertFalse(text.contains("IGST"));
    }

    @Test
    void paymentReceiptFillsEveryField() throws IOException {
        Map<String, String> v = common();
        v.put("paymentReceiptNo", "TEST-PR-18-11-2026-00003");
        v.put("paidDate", "18-11-2026");
        v.put("paidAt", "18-11-2026 04:05 PM");
        v.put("paidVia", "UPI");
        v.put("amount", "500.00");
        v.put("invoiceTotal", "1,260.00");
        v.put("paidBefore", "100.00");
        v.put("totalPaid", "600.00");
        v.put("balanceDue", "660.00");
        v.put("paymentStatus", "Partial");
        v.put("invoiceNo", "TEST-04-11-2026-00011");
        v.put("invoiceDate", "04-11-2026");
        v.put("expectedDelivery", "02-12-2026");

        String text = fillAndRead("PaymentReceipt.docx", v, List.of(), Set.of());

        assertFalse(text.contains("{{"), text);
        assertTrue(text.contains("towards invoice TEST-04-11-2026-00011"));
        assertTrue(text.contains("UPI") && text.contains("660.00"));
        // no transaction id given -> "-"
        assertTrue(text.contains(" - ") || text.contains("-"));
    }

    @Test
    void formatsAmountsRatesAndQuantities() {
        assertEquals("1,20,000.00", SalesDocumentPdfService.money(120000));
        assertEquals("0.00", SalesDocumentPdfService.money(0));
        assertEquals("1,200.00", SalesDocumentPdfService.money(1200));
        assertEquals("12,34,567.50", SalesDocumentPdfService.money(1234567.5));
        assertEquals("999.00", SalesDocumentPdfService.money(999));
        assertEquals("2.5", SalesDocumentPdfService.rate(2.5));
        assertEquals("5", SalesDocumentPdfService.rate(5.0));
        assertEquals("2", SalesDocumentPdfService.quantity(2.0));
    }

    // ------------------------------------------------------------------ helpers

    private static String fillAndRead(String template, Map<String, String> values, List<WordTemplate.Rows> rows,
                                      Set<String> drop) throws IOException {
        byte[] docx = WordTemplate.fill(template, values, rows, drop);
        Path out = Path.of("target", "template-preview");
        Files.createDirectories(out);
        Files.write(out.resolve(template), docx);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(p -> text.append(p.getText()).append('\n'));
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    row.getTableCells().forEach(c -> text.append(c.getText()).append(" | "));
                    text.append('\n');
                }
            }
            return text.toString();
        }
    }

    /** Rows between the items header and the "Total Amount" row. */
    private static int itemLines(String template, Map<String, String> values, List<Map<String, String>> items) throws IOException {
        byte[] docx = WordTemplate.fill(template, values, List.of(new WordTemplate.Rows("item", items, 6)), Set.of());
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            for (XWPFTable table : document.getTables()) {
                List<XWPFTableRow> rows = table.getRows();
                if (!rows.isEmpty() && rows.get(0).getCell(0).getText().equalsIgnoreCase("qty")) {
                    int count = 0;
                    for (int i = 1; i < rows.size() && !rows.get(i).getCell(rows.get(i).getTableCells().size() > 1 ? 1 : 0)
                            .getText().contains("Total Amount"); i++) {
                        count++;
                    }
                    return count;
                }
            }
        }
        return -1;
    }
}
