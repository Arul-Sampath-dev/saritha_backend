package com.saritha.fashions.modules.core.pdf;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/**
 * Builds the payment receipt (.docx, converted to PDF by DocxToPdfConverter): one page with the
 * shop header, receipt details and the invoice's paid / balance position after this payment.
 *
 * Built in code (no Word template), so it needs no separately designed file. Amounts always show
 * 2 decimals; "Rs." is used instead of the rupee sign so it renders with any converter font.
 */
final class PaymentReceiptDocument {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a", Locale.ENGLISH);

    private PaymentReceiptDocument() {
    }

    /** Everything printed on the payment receipt. Null texts print as "-". */
    record Data(String shopName, String shopAddress, String shopPhone, String shopGst,
                String paymentReceiptNo, Date paidAt, String customerName, String customerPhone,
                String invoiceId, double amount, String paidVia, String transactionId,
                double invoiceTotalWithGST, double totalPaid, double balanceDue, String paymentStatus) {
    }

    static byte[] build(Data data) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            paragraph(document, text(data.shopName()), 16, true, ParagraphAlignment.CENTER);
            paragraph(document, text(data.shopAddress()), 10, false, ParagraphAlignment.CENTER);
            String contact = "Phone: " + text(data.shopPhone())
                    + (data.shopGst() == null || data.shopGst().isBlank() ? "" : "   |   GSTIN: " + data.shopGst());
            paragraph(document, contact, 10, false, ParagraphAlignment.CENTER);
            paragraph(document, "PAYMENT RECEIPT", 14, true, ParagraphAlignment.CENTER);

            XWPFTable details = document.createTable();
            row(details, true, "Receipt No.", text(data.paymentReceiptNo()));
            row(details, false, "Date", data.paidAt() == null ? "-" : DATE_TIME.format(data.paidAt().toInstant().atZone(IST)));
            row(details, false, "Customer", text(data.customerName()));
            row(details, false, "Customer Phone", text(data.customerPhone()));
            row(details, false, "Invoice No.", text(data.invoiceId()));
            row(details, false, "Amount Received", money(data.amount()));
            row(details, false, "Payment Method", text(data.paidVia()));
            if (data.transactionId() != null && !data.transactionId().isBlank()) {
                row(details, false, "Transaction ID", data.transactionId());
            }

            paragraph(document, "Invoice position after this payment", 11, true, ParagraphAlignment.LEFT);
            XWPFTable position = document.createTable();
            row(position, true, "Invoice Total (incl. GST)", money(data.invoiceTotalWithGST()));
            row(position, false, "Total Paid So Far", money(data.totalPaid()));
            row(position, false, "Balance Due", money(data.balanceDue()));
            row(position, false, "Payment Status", text(data.paymentStatus()));

            paragraph(document, "Thank you for your payment.", 10, false, ParagraphAlignment.CENTER);
            paragraph(document, "This receipt confirms a part / full payment against the invoice above. "
                    + "The final receipt is issued on delivery.", 8, false, ParagraphAlignment.CENTER);

            document.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new PdfGenerationException("Could not build the payment receipt document", exception);
        }
    }

    static String money(double value) {
        return "Rs. " + BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static void paragraph(XWPFDocument document, String text, int size, boolean bold, ParagraphAlignment alignment) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment);
        paragraph.setSpacingAfter(120);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontSize(size);
    }

    /** Adds a label / value row; the first call fills the row the new table starts with. */
    private static void row(XWPFTable table, boolean first, String label, String value) {
        XWPFTableRow row = first ? table.getRow(0) : table.createRow();
        while (row.getTableCells().size() < 2) {
            row.addNewTableCell();
        }
        row.getCell(0).setText(label);
        row.getCell(1).setText(value);
        row.getCell(0).setWidth("3500");
        row.getCell(1).setWidth("5500");
    }
}
