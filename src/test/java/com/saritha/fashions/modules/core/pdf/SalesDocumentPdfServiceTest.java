package com.saritha.fashions.modules.core.pdf;

import com.mongodb.DBRef;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.context.support.ServletRequestHandledEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Renders the three documents end to end (Mongo mocked, real templates and real PDF renderer) and checks the text.
 * The PDFs are also written to target/pdf-preview/ to look at.
 */
class SalesDocumentPdfServiceTest {

    private static final ObjectId SHOP_ID = new ObjectId();
    private static final ObjectId CUSTOMER_ID = new ObjectId();
    private static final ObjectId INVOICE_DB_ID = new ObjectId();

    private MongoTemplate mongo;
    private SalesDocumentPdfService service;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        service = new SalesDocumentPdfService(mongo, new HtmlPdfRenderer());
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("shops"))).thenReturn(new Document()
                .append("_id", SHOP_ID)
                .append("shopAddress", "No 12, OMR Road, Navalur, Chennai 600130")
                .append("shopContactNumber", "9876543210")
                .append("gstNumber", "33ABCDE1234F1Z5"));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("customers"))).thenReturn(new Document()
                .append("_id", CUSTOMER_ID).append("name", "Priya Ramanathan").append("phone", "9123456780"));
    }

    @Test
    void invoiceWithCgstSgst() throws Exception {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("invoice"))).thenReturn(invoice(2.5, 0));
        String text = text(save("invoice-cgst-sgst", service.invoicePdf("SARI-23-11-2026-00042", null)));

        assertTrue(text.contains("TAX INVOICE"));
        assertTrue(text.contains("SARI-23-11-2026-00042"));
        assertTrue(text.contains("Priya Ramanathan"));
        assertTrue(text.contains("Bridal Blouse"));
        assertTrue(text.contains("1,20,000.00"), "Indian grouping");
        assertTrue(text.contains("CGST (2.5%)"));
        assertTrue(text.contains("SGST (2.5%)"));
        assertFalse(text.contains("IGST"));
        assertTrue(text.contains("₹"), "rupee sign renders");
    }

    @Test
    void invoiceWithIgstAndNoGst() throws Exception {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("invoice"))).thenReturn(invoice(0, 5));
        String igst = text(save("invoice-igst", service.invoicePdf("SARI-23-11-2026-00042", null)));
        assertTrue(igst.contains("IGST (5%)"));
        assertFalse(igst.contains("CGST"));

        when(mongo.findOne(any(Query.class), eq(Document.class), eq("invoice"))).thenReturn(invoice(0, 0));
        service.onRequestHandled(changeRequest()); // the invoice was edited: the cached PDF is dropped
        String none = text(save("invoice-no-gst", service.invoicePdf("SARI-23-11-2026-00042", null)));
        assertFalse(none.contains("CGST") || none.contains("SGST") || none.contains("IGST"));
    }

    @Test
    void invoiceWithManyItemsRunsOntoMorePages() throws Exception {
        Document invoice = invoice(2.5, 0);
        List<Document> items = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            items.add(item("Item number " + i, 1, 500));
        }
        invoice.put("items", items);
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("invoice"))).thenReturn(invoice);
        byte[] pdf = save("invoice-40-items", service.invoicePdf("SARI-23-11-2026-00042", null));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() >= 2);
        }
        assertTrue(text(pdf).contains("Item number 40"));
    }

    @Test
    void receiptListsEveryPayment() throws Exception {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("receipt"))).thenReturn(new Document()
                .append("receiptNo", "SARI-R-23-11-2026-00007").append("shopId", SHOP_ID.toHexString())
                .append("invoiceId", INVOICE_DB_ID.toHexString())
                .append("customer", new DBRef("customers", CUSTOMER_ID))
                .append("receiptDate", date(2026, 11, 23, 17, 30))
                .append("items", invoice(2.5, 0).get("items"))
                .append("totalAmount", 7500.0).append("discount", 0.0).append("taxableAmount", 7500.0)
                .append("cgstRate", 2.5).append("sgstRate", 2.5).append("cgstAmount", 187.5).append("sgstAmount", 187.5)
                .append("totalAmountWithGST", 7875.0).append("advancePaid", 7875.0).append("balanceDue", 0.0));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("invoice"))).thenReturn(invoice(2.5, 0));

        String text = text(save("receipt", service.receiptPdf("SARI-R-23-11-2026-00007", null)));
        assertTrue(text.contains("RECEIPT"));
        assertTrue(text.contains("PAYMENT DETAILS"));
        assertTrue(text.contains("Advance (with invoice)"));
        assertTrue(text.contains("SARI-PR-23-11-2026-00003"));
        assertTrue(text.contains("UPI-778812"));
    }

    @Test
    void paymentReceipt() throws Exception {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("payment_receipts"))).thenReturn(new Document()
                .append("paymentReceiptNo", "SARI-PR-23-11-2026-00003").append("shopId", SHOP_ID.toHexString())
                .append("invoiceDbId", INVOICE_DB_ID.toHexString()).append("invoiceId", "SARI-23-11-2026-00042")
                .append("customer", new DBRef("customers", CUSTOMER_ID))
                .append("paidAt", date(2026, 11, 23, 11, 5)).append("paidVia", "UPI").append("transactionId", "UPI-778812")
                .append("amount", 4875.0).append("totalPaid", 7875.0).append("invoiceTotalWithGST", 7875.0)
                .append("balanceDue", 0.0).append("paymentStatus", "Paid"));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("invoice"))).thenReturn(invoice(2.5, 0));

        String text = text(save("payment-receipt", service.paymentReceiptPdf("SARI-PR-23-11-2026-00003", null)));
        assertTrue(text.contains("PAYMENT RECEIPT"));
        assertTrue(text.contains("4,875.00"));
        assertTrue(text.contains("3,000.00"), "paid before = total paid - this payment");
        assertTrue(text.contains("towards invoice SARI-23-11-2026-00042."));
    }

    @Test
    void aPdfIsBuiltOnceUntilSomethingChanges() {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("invoice"))).thenReturn(invoice(2.5, 0));
        byte[] first = service.invoicePdf("SARI-23-11-2026-00042", null);
        byte[] again = service.invoicePdf("SARI-23-11-2026-00042", null);
        assertSame(first, again, "served from the cache");
        verify(mongo, times(1)).findOne(any(Query.class), eq(Document.class), eq("invoice"));

        service.onRequestHandled(new ServletRequestHandledEvent(this, "/x", "1", "GET", "d", null, null, 1, null, 200));
        assertSame(first, service.invoicePdf("SARI-23-11-2026-00042", null), "a read changes nothing");

        service.onRequestHandled(changeRequest());
        assertNotSame(first, service.invoicePdf("SARI-23-11-2026-00042", null), "rebuilt after a change");
        verify(mongo, times(2)).findOne(any(Query.class), eq(Document.class), eq("invoice"));
    }

    @Test
    void anotherShopScopeIsNotServedFromTheCache() {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("invoice"))).thenReturn(invoice(2.5, 0));
        service.invoicePdf("SARI-23-11-2026-00042", null);
        service.invoicePdf("SARI-23-11-2026-00042", "other-shop");
        verify(mongo, times(2)).findOne(any(Query.class), eq(Document.class), eq("invoice"));
    }

    private ServletRequestHandledEvent changeRequest() {
        return new ServletRequestHandledEvent(this, "/v1/api/invoices/x/payments", "1", "POST", "d", null, null, 1, null, 201);
    }

    @Test
    void missingValuesPrintADash() {
        Map<String, String> printable = SalesDocumentPdfService.printable(new java.util.HashMap<>(Map.of("a", " ")));
        assertEquals("-", printable.get("a"));
        assertEquals("-", printable.get("notGiven"));
        Map<String, Object> model = SalesDocumentPdfService.model(Map.of(), Set.of("igstAmount"), "x");
        assertEquals(true, model.get("showCgstSgst"));
        assertEquals(false, model.get("showIgst"));
    }

    // ------------------------------------------------------------------ fixtures

    private static Document invoice(double cgstRate, double igstRate) {
        double taxable = 120000 + 2 * 1500;
        Document invoice = new Document()
                .append("_id", INVOICE_DB_ID).append("invoiceId", "SARI-23-11-2026-00042")
                .append("shopId", SHOP_ID.toHexString())
                .append("customer", new DBRef("customers", CUSTOMER_ID))
                .append("invoiceDate", date(2026, 11, 20, 10, 0))
                .append("paymentMethod", "UPI").append("paymentStatus", "Partial").append("hsnCode", "998821")
                .append("expectedDeliveryDate", "2026-11-30")
                .append("items", List.of(item("Bridal Blouse", 1, 120000), item("Saree Fall & Pico", 2, 1500)))
                .append("totalAmount", taxable).append("discount", 0.0).append("taxableAmount", taxable)
                .append("advancePaid", 3000.0)
                .append("paymentHistories", List.of(
                        new Document("amount", 3000.0).append("paidAt", date(2026, 11, 20, 10, 0)).append("paidVia", "Cash"),
                        new Document("amount", 4875.0).append("paidAt", date(2026, 11, 23, 11, 5)).append("paidVia", "UPI")
                                .append("transactionId", "UPI-778812").append("paymentReceiptNo", "SARI-PR-23-11-2026-00003")));
        double gst = taxable * (cgstRate * 2 + igstRate) / 100;
        if (cgstRate > 0) {
            invoice.append("cgstRate", cgstRate).append("sgstRate", cgstRate)
                    .append("cgstAmount", gst / 2).append("sgstAmount", gst / 2);
        }
        if (igstRate > 0) {
            invoice.append("igstRate", igstRate).append("igstAmount", gst);
        }
        invoice.append("totalAmountWithGST", taxable + gst).append("balanceDue", taxable + gst - 3000);
        return invoice;
    }

    private static Document item(String name, int quantity, double price) {
        return new Document("itemName", name).append("itemQuantity", quantity).append("itemPrice", price);
    }

    private static Date date(int year, int month, int day, int hour, int minute) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.of("Asia/Kolkata")).toInstant());
    }

    private static byte[] save(String name, byte[] pdf) throws Exception {
        assertTrue(pdf.length > 1000);
        assertEquals("%PDF", new String(pdf, 0, 4));
        Path dir = Path.of("target", "pdf-preview");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name + ".pdf"), pdf);
        return pdf;
    }

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
