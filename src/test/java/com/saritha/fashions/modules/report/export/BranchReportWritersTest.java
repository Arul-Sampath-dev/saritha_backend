package com.saritha.fashions.modules.report.export;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.modules.core.pdf.DocxToPdfConverter;
import com.saritha.fashions.modules.report.export.BranchReportData.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BranchReportWritersTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // ---------------------------------------------------------------- fixture

    private static Date at(int day, int hour) {
        return Date.from(LocalDateTime.of(2026, 11, day, hour, 0).atZone(IST).toInstant());
    }

    static BranchReportData sample(int orderCount, LocalDate from, LocalDate to) {
        Header header = new Header("shop1", "Saritha Fashions - Navalur", "12, OMR Main Road", "98410 12345", "33ABCDE1234F1Z5",
                "Priya R", from, to, from.minusDays(30), from.minusDays(1), LocalDateTime.of(2026, 11, 30, 18, 42), "Platform owner");
        Figures current = new Figures(orderCount, 3150, 3000, 150, 0, 2000, 2, 700, 2, 500, 200, 1300, 1, 3, 1150, 5000, 2340, 1);
        Figures previous = new Figures(10, 2800, 2667, 133, 0, 2100, 3, 800, 2, 600, 200, 1300, 1, 2, 900, 0, 0, 0);
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < orderCount; i++) {
            orders.add(new Order("SARI-01-11-2026-" + String.format("%05d", i + 1), at(1, 11), "Anitha S", "9876543210", 1, 2,
                    "Blouse, Saree fall & pico", 1500, 75, 1575, 1000, 575, "Partial", "In production", "2026-11-10", null));
        }
        List<Day> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.add(new Day(d, d.equals(from) ? 2 : 0, d.equals(from) ? 3150 : 0, d.equals(from) ? 2000 : 0, d.equals(from) ? 700 : 0,
                    d.equals(from) ? 1300 : 0));
        }
        return new BranchReportData(header, current, previous,
                List.of(new GstRow("Intra-state (CGST + SGST)", 2, 3000, 75, 75, 0, 3150), new GstRow("Inter-state (IGST)", 0, 0, 0, 0, 0, 0)),
                List.of(new Amount("Cash", 1, 1200), new Amount("UPI", 1, 800)),
                List.of(new Amount("Rent", 1, 500), new Amount("Advance", 1, 200)),
                days, orders,
                List.of(new Payment(at(1, 11), "SARI-PR-01-11-2026-00001", "SARI-01-11-2026-00001", "Anitha S", "Cash", null, 1200, "Priya R"),
                        new Payment(at(2, 12), null, "SARI-01-11-2026-00002", "Meena K", "UPI", "UPI-1", 800, null)),
                List.of(new Delivery("SARI-05-11-2026-00001", at(5, 17), "SARI-01-11-2026-00001", "Anitha S", 1575, "Cash")),
                List.of(new Expense(at(1, 10), "Rent", "Shop", "Cash", "November rent", 500),
                        new Expense(at(3, 10), "Advance", "Meena S", "Cash", null, 200)),
                List.of(new DeletedExpense(at(4, 10), "Materials", 640, at(4, 17), "Priya R", "Entered twice")),
                List.of(new TopUp(at(2, 10), "Platform owner", 5000)),
                List.of(new StageNow("Item accepted", 1, 0, 1.2), new StageNow("Stitching", 2, 1, 3.4)),
                new Turnaround(1, 4.0, 100.0, 1),
                List.of(new StageTime("Stitching", 86, 3), new StageTime("Ironing", 5, 3)),
                List.of(new Staff("Meena S", "Tailor", "2 tailor completed", 2, at(5, 16))),
                new Customers(2, 1, 1, 50.0, 1575),
                List.of(new TopCustomer("Anitha S", "9876543210", 1, 1575, at(1, 11))),
                List.of(new DeletedInvoice("SARI-07-11-2026-00049", at(7, 10), "Harini T", 1470, at(7, 16), "Priya R", "Customer cancelled")),
                List.of(new ItemSold("Blouse", 2, 2800), new ItemSold("Saree fall & pico", 1, 250)));
    }

    // ---------------------------------------------------------------- Excel

    @Test
    void excelHasEverySheetNumbersAsNumbersAndSumTotals() throws Exception {
        byte[] bytes = new BranchReportExcelWriter().write(sample(2, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            List<String> names = new ArrayList<>();
            wb.forEach(s -> names.add(s.getSheetName()));
            assertEquals(List.of("Summary", "Daily", "Orders", "Payments", "Deliveries", "Expenses", "Petty cash", "Production",
                    "Staff", "Customers", "Deleted invoices", "Items"), names);

            Sheet orders = wb.getSheet("Orders");
            Row header = find(orders, "Invoice no");
            assertNotNull(header, "orders header row");
            Row first = orders.getRow(header.getRowNum() + 1);
            Cell total = first.getCell(8);
            assertEquals(CellType.NUMERIC, total.getCellType());
            assertEquals(1575.0, total.getNumericCellValue());
            assertTrue(total.getCellStyle().getDataFormatString().contains("₹"), "money format");
            Row totals = orders.getRow(header.getRowNum() + 3);
            assertEquals("Total", totals.getCell(0).getStringCellValue());
            assertEquals(CellType.FORMULA, totals.getCell(8).getCellType());
            assertTrue(totals.getCell(8).getCellFormula().startsWith("SUM(I"));

            Sheet summary = wb.getSheet("Summary");
            assertNotNull(find(summary, "GST summary (business summary — not a GST return)"));
            assertNotNull(find(summary, "Collected by payment method"));
            Sheet deliveries = wb.getSheet("Deliveries");
            Row dh = find(deliveries, "Receipt no");
            assertEquals("SARI-05-11-2026-00001", deliveries.getRow(dh.getRowNum() + 1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void excelEmptyListSaysNone() throws Exception {
        BranchReportData base = sample(0, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));
        byte[] bytes = new BranchReportExcelWriter().write(base);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet orders = wb.getSheet("Orders");
            Row header = find(orders, "Invoice no");
            assertEquals("None in this period", orders.getRow(header.getRowNum() + 1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void excelKeepsEveryRowEvenOverThePdfCap() throws Exception {
        byte[] bytes = new BranchReportExcelWriter().write(sample(620, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet orders = wb.getSheet("Orders");
            Row header = find(orders, "Invoice no");
            assertEquals("Total", orders.getRow(header.getRowNum() + 621).getCell(0).getStringCellValue());
        }
    }

    // ---------------------------------------------------------------- PDF (Word document before conversion)

    @Test
    void documentHasTheSectionsAndFigures() throws Exception {
        BranchReportPdfWriter writer = new BranchReportPdfWriter(mock(DocxToPdfConverter.class));
        String text = text(writer.docx(sample(2, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))));
        for (String expected : List.of("Saritha Fashions - Navalur", "Summary", "₹3,150", "GST summary", "Day by day", "Orders",
                "Payments received", "Deliveries", "Expenses", "Deleted expenses", "Petty cash top-ups", "Production right now",
                "Where the time goes", "Staff work", "Customers", "Sales by item", "Deleted invoices", "₹1,575.00", "Slowest")) {
            assertTrue(text.contains(expected), "missing: " + expected);
        }
        assertFalse(text.contains("{{"));
    }

    @Test
    void documentCapsLongListsAndGoesWeeklyForLongPeriods() throws Exception {
        BranchReportPdfWriter writer = new BranchReportPdfWriter(mock(DocxToPdfConverter.class));
        String text = text(writer.docx(sample(620, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))));
        assertTrue(text.contains("+ 120 more orders — see the Excel report"));
        assertTrue(text.contains("Week by week"));
        assertFalse(text.contains("Day by day"));
    }

    @Test
    void moneyUsesIndianGrouping() {
        assertEquals("₹1,23,45,678.90", BranchReportPdfWriter.money(12345678.9));
        assertEquals("₹999.00", BranchReportPdfWriter.money(999));
        assertEquals("-₹1,500.50", BranchReportPdfWriter.money(-1500.5));
        assertEquals("₹2,25,015", BranchReportPdfWriter.money0(225015.2));
    }

    // ---------------------------------------------------------------- service helpers, period, file name

    @Test
    void gstSplitsIntraAndInterState() {
        List<Document> invoices = List.of(
                new Document("taxableAmount", 1000.0).append("cgstAmount", 25.0).append("sgstAmount", 25.0).append("igstAmount", 0.0),
                new Document("taxableAmount", 2000.0).append("cgstAmount", 50.0).append("sgstAmount", 50.0).append("igstAmount", 0.0),
                new Document("taxableAmount", 500.0).append("cgstAmount", 0.0).append("sgstAmount", 0.0).append("igstAmount", 25.0));
        List<GstRow> rows = BranchReportService.gstRows(invoices);
        assertEquals(2, rows.get(0).invoices());
        assertEquals(3000.0, rows.get(0).taxable());
        assertEquals(75.0, rows.get(0).cgst());
        assertEquals(3150.0, rows.get(0).total());
        assertEquals(1, rows.get(1).invoices());
        assertEquals(25.0, rows.get(1).igst());
        assertEquals(525.0, rows.get(1).total());
    }

    @Test
    void itemsSoldAddsUpPiecesAndAmounts() {
        List<Document> invoices = List.of(
                new Document("items", List.of(new Document("itemName", "Blouse").append("itemQuantity", 2).append("itemPrice", 1400.0),
                        new Document("itemName", "Kurti").append("itemQuantity", 1).append("itemPrice", 950.0))),
                new Document("items", List.of(new Document("itemName", "Blouse").append("itemQuantity", 1).append("itemPrice", 1400.0))));
        List<ItemSold> items = BranchReportService.itemsSold(invoices);
        assertEquals("Blouse", items.get(0).name());
        assertEquals(3.0, items.get(0).pieces());
        assertEquals(4200.0, items.get(0).amount());
        assertEquals("Kurti", items.get(1).name());
    }

    @Test
    void periodRules() {
        assertEquals(1, BranchReportService.checkPeriod(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19)));
        assertEquals(366, BranchReportService.checkPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)));
        assertThrows(APIException.class, () -> BranchReportService.checkPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2)));
        assertThrows(APIException.class, () -> BranchReportService.checkPeriod(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        assertThrows(APIException.class, () -> BranchReportService.checkPeriod(null, LocalDate.of(2026, 1, 1)));
    }

    @Test
    void fileNames() {
        assertEquals("SarithaFashions_Navalur_2026-11-01_2026-11-30.xlsx",
                BranchReportController.fileName("Saritha Fashions - Navalur", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "xlsx"));
        assertEquals("SarithaFashions_Navalur_2026-11-19.pdf",
                BranchReportController.fileName("Saritha Fashions - Navalur", LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19), "pdf"));
        assertEquals("SarithaFashions_T-Nagar-Main_2026-11-19.pdf",
                BranchReportController.fileName("T. Nagar / Main", LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19), "pdf"));
    }

    // ---------------------------------------------------------------- helpers

    private static Row find(Sheet sheet, String firstCell) {
        for (Row row : sheet) {
            Cell cell = row.getCell(0);
            if (cell != null && cell.getCellType() == CellType.STRING && firstCell.equals(cell.getStringCellValue())) {
                return row;
            }
        }
        return null;
    }

    private static String text(byte[] docx) throws Exception {
        // a long, repetitive table compresses very well; POI's zip-bomb guard would refuse to read it back here
        org.apache.poi.openxml4j.util.ZipSecureFile.setMinInflateRatio(0.001);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }
}
