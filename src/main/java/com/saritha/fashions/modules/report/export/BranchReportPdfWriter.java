package com.saritha.fashions.modules.report.export;

import com.saritha.fashions.modules.core.pdf.DocxToPdfConverter;
import com.saritha.fashions.modules.core.pdf.PdfGenerationException;
import com.saritha.fashions.modules.report.export.BranchReportData.Figures;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.TableWidthType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Writes the branch report as a PDF: the Word document is built here with POI (A4, logo, green title band, summary
 * figures, then one section per list, footer with page numbers) and turned into a PDF by the same
 * {@link DocxToPdfConverter} as the invoices. Lists are capped at {@link #MAX_ROWS} rows (the Excel has them all);
 * periods longer than {@link #WEEKLY_AFTER_DAYS} days show the day-by-day table per week.
 */
@Component
public class BranchReportPdfWriter {

    static final int MAX_ROWS = 500;
    static final int WEEKLY_AFTER_DAYS = 62;
    /** A4 width (11906) minus the left and right margins (737 each), in twips. */
    private static final int CONTENT_WIDTH_TWIPS = 11906 - 2 * 737;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private static final String INK = "17171C";
    private static final String SLATE = "75758A";
    private static final String MUTED = "93939F";
    private static final String GREEN = "003C33";
    private static final String HEAD_FILL = "F7F6F3";
    private static final String TOTAL_FILL = "EDFCE9";
    private static final String RED = "B30000";
    private static final String OK = "1A7F4B";
    private static final String FONT = "Segoe UI";

    private final DocxToPdfConverter converter;

    public BranchReportPdfWriter(DocxToPdfConverter converter) {
        this.converter = converter;
    }

    public byte[] write(BranchReportData data, String fileName) {
        return converter.convert(docx(data), fileName.replaceAll("\\.pdf$", ".docx"));
    }

    /** The Word document (before the PDF conversion). */
    byte[] docx(BranchReportData data) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            page(doc);
            footer(doc, data);
            cover(doc, data);
            moneyDetail(doc, data);

            heading(doc, data.days().size() > WEEKLY_AFTER_DAYS ? "Week by week" : "Day by day", null, true);
            dayTable(doc, data);

            heading(doc, "Orders", count(data.orders().size(), "invoice", "invoices") + " dated in the period", true);
            orders(doc, data);

            heading(doc, "Payments received", count(data.payments().size(), "payment", "payments"), true);
            payments(doc, data);

            heading(doc, "Deliveries", count(data.deliveries().size(), "order", "orders") + " handed over", true);
            Table t = new Table(doc, new String[]{"Receipt", "Delivered on", "Invoice", "Customer", "Total"}, new int[]{4}, new int[]{24, 19, 23, 20, 14});
            List<String[]> rows = new ArrayList<>();
            data.deliveries().forEach(d -> rows.add(new String[]{d.receiptNo(), dateTime(d.deliveredAt()), d.invoiceNo(), d.customer(), money(d.total())}));
            t.rows(rows, "orders");
            t.total(new String[]{"Total", "", "", count(data.deliveries().size(), "order", "orders"), money(sum(data.deliveries().stream().mapToDouble(BranchReportData.Delivery::total)))});

            heading(doc, "Expenses", count(data.expenses().size(), "entry", "entries"), false);
            t = new Table(doc, new String[]{"Date", "Category", "For", "Method", "Notes", "Amount"}, new int[]{5}, new int[]{13, 15, 15, 12, 30, 15});
            rows.clear();
            data.expenses().forEach(x -> rows.add(new String[]{date(x.date()), x.category(), x.forWhom(), x.method(), nz(x.notes()), money(x.amount())}));
            t.rows(rows, "expenses");
            t.total(new String[]{"Total", "", "", "", count(data.expenses().size(), "entry", "entries"), money(data.current().expenses())});

            heading(doc, "Deleted expenses", null, false);
            t = new Table(doc, new String[]{"Date", "Category", "Amount", "Deleted on", "By", "Reason"}, new int[]{2}, new int[]{13, 15, 14, 20, 14, 24});
            rows.clear();
            data.deletedExpenses().forEach(x -> rows.add(new String[]{date(x.date()), x.category(), money(x.amount()), dateTime(x.deletedAt()), x.deletedBy(), nz(x.reason())}));
            t.rows(rows, "deleted expenses");

            heading(doc, "Petty cash top-ups", "balance now " + money(data.current().pettyBalance()), false);
            t = new Table(doc, new String[]{"Added on", "Added by", "Amount"}, new int[]{2}, new int[]{40, 35, 25});
            rows.clear();
            data.topUps().forEach(x -> rows.add(new String[]{dateTime(x.addedAt()), x.addedBy(), money(x.amount())}));
            t.rows(rows, "top-ups");
            t.total(new String[]{"Added in the period", "", money(data.current().pettyAdded())});

            production(doc, data);
            people(doc, data);

            doc.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new PdfGenerationException("Could not build the branch report document", exception);
        }
    }

    // ================================================================ sections

    private void cover(XWPFDocument doc, BranchReportData data) throws IOException {
        BranchReportData.Header h = data.header();
        XWPFParagraph top = doc.createParagraph();
        try (InputStream logo = new ClassPathResource("report/logo.png").getInputStream()) {
            top.createRun().addPicture(logo, Document.PICTURE_TYPE_PNG, "logo.png", Units.toEMU(120), Units.toEMU(38));
        } catch (Exception ignored) {
            text(top, "Saritha's Stitch Hub", 14, true, INK);
        }

        // green title band
        XWPFTable band = doc.createTable(1, 1);
        noBorders(band);
        band.setWidth("100%");
        XWPFTableCell cell = band.getRow(0).getCell(0);
        cell.setColor(GREEN);
        XWPFParagraph p = cell.getParagraphs().get(0);
        spacing(p, 120, 0);
        text(p, "BRANCH REPORT", 8, false, "B3C7C2");
        p = cell.addParagraph();
        text(p, nz(h.branchName()), 20, true, "FFFFFF");
        p = cell.addParagraph();
        text(p, "Period ", 9, false, "D5E2DE");
        text(p, BranchReportExcelWriter.periodText(h.from(), h.to()), 9, true, "FFFFFF");
        text(p, "     Shop owner ", 9, false, "D5E2DE");
        text(p, nz(h.ownerName()), 9, true, "FFFFFF");
        if (h.gstin() != null && !h.gstin().isBlank()) {
            text(p, "     GSTIN ", 9, false, "D5E2DE");
            text(p, h.gstin(), 9, true, "FFFFFF");
        }
        p = cell.addParagraph();
        spacing(p, 0, 120);
        text(p, nz(h.address()) + (h.phone() == null ? "" : " · " + h.phone()), 9, false, "D5E2DE");

        heading(doc, "Summary", "▲ / ▼ compared with " + BranchReportExcelWriter.periodText(h.previousFrom(), h.previousTo()), false);
        Figures c = data.current();
        Figures prev = data.previous();
        String[][] kpis = {
                {"SALES (INVOICED)", money0(c.sales()), count(c.orders(), "order", "orders"), change(c.sales(), prev.sales(), true)},
                {"COLLECTED", money0(c.collected()), count(c.payments(), "payment", "payments"), change(c.collected(), prev.collected(), true)},
                {"EXPENSES", money0(c.expenses()), count(c.expenseEntries(), "entry", "entries"), change(c.expenses(), prev.expenses(), false)},
                {"NET CASH FLOW", money0(c.net()), "collected − expenses", change(c.net(), prev.net(), true)},
                {"ORDERS DELIVERED", String.valueOf(c.delivered()), "receipts generated", ""},
                {"OUTSTANDING NOW", money0(c.outstanding()), count(c.openInvoices(), "open invoice", "open invoices"), ""},
                {"PETTY CASH", money0(c.pettyBalance()), "balance · " + money0(c.pettyAdded()) + " added", ""},
                {"DELETED INVOICES", String.valueOf(c.deletedInvoices()), "in the period", ""},
        };
        XWPFTable grid = doc.createTable(2, 4);
        grid.setWidth("100%");
        borders(grid, "E5E7EB");
        for (int i = 0; i < kpis.length; i++) {
            XWPFTableCell k = grid.getRow(i / 4).getCell(i % 4);
            XWPFParagraph a = k.getParagraphs().get(0);
            spacing(a, 80, 0);
            text(a, kpis[i][0], 7, false, MUTED);
            XWPFParagraph b = k.addParagraph();
            text(b, kpis[i][1], 14, true, INK);
            XWPFParagraph d = k.addParagraph();
            spacing(d, 0, 80);
            text(d, kpis[i][2] + " ", 7.5, false, SLATE);
            String ch = kpis[i][3];
            if (!ch.isEmpty()) {
                text(d, ch.substring(1), 7.5, true, ch.charAt(0) == '+' ? OK : RED);
            }
        }
    }

    private void moneyDetail(XWPFDocument doc, BranchReportData data) {
        Figures c = data.current();
        heading(doc, "Sales", null, false);
        Table t = new Table(doc, new String[]{"", "Amount"}, new int[]{1}, new int[]{70, 30});
        t.rows(List.of(
                new String[]{"Gross (items)", money(c.taxable() + c.discount())},
                new String[]{"Discount", "− " + money(c.discount())},
                new String[]{"Taxable value", money(c.taxable())},
                new String[]{"GST", money(c.gst())}), null);
        t.total(new String[]{"Sales incl. GST", money(c.sales())});

        heading(doc, "GST summary", "business summary — not a GST return", false);
        t = new Table(doc, new String[]{"Type", "Invoices", "Taxable", "CGST", "SGST", "IGST", "Total"},
                new int[]{1, 2, 3, 4, 5, 6}, new int[]{28, 10, 14, 12, 12, 12, 12});
        List<String[]> rows = new ArrayList<>();
        data.gst().forEach(g -> rows.add(new String[]{g.label(), String.valueOf(g.invoices()), money(g.taxable()), money(g.cgst()),
                money(g.sgst()), money(g.igst()), money(g.total())}));
        t.rows(rows, null);
        t.total(new String[]{"Total", String.valueOf(data.gst().stream().mapToLong(BranchReportData.GstRow::invoices).sum()),
                money(sum(data.gst().stream().mapToDouble(BranchReportData.GstRow::taxable))),
                money(sum(data.gst().stream().mapToDouble(BranchReportData.GstRow::cgst))),
                money(sum(data.gst().stream().mapToDouble(BranchReportData.GstRow::sgst))),
                money(sum(data.gst().stream().mapToDouble(BranchReportData.GstRow::igst))),
                money(sum(data.gst().stream().mapToDouble(BranchReportData.GstRow::total)))});

        heading(doc, "Collected by payment method", null, false);
        t = new Table(doc, new String[]{"Method", "Payments", "Amount"}, new int[]{1, 2}, new int[]{50, 20, 30});
        rows.clear();
        data.collectedByMethod().forEach(m -> rows.add(new String[]{m.name(), String.valueOf(m.count()), money(m.amount())}));
        t.rows(rows, "payments");
        t.total(new String[]{"Total", String.valueOf(c.payments()), money(c.collected())});

        heading(doc, "Expenses by category", null, false);
        t = new Table(doc, new String[]{"Category", "Entries", "Amount"}, new int[]{1, 2}, new int[]{50, 20, 30});
        rows.clear();
        data.expensesByCategory().forEach(m -> rows.add(new String[]{m.name(), String.valueOf(m.count()), money(m.amount())}));
        t.rows(rows, "expenses");
        t.total(new String[]{"Total", String.valueOf(c.expenseEntries()), money(c.expenses())});
    }

    private void dayTable(XWPFDocument doc, BranchReportData data) {
        boolean weekly = data.days().size() > WEEKLY_AFTER_DAYS;
        Table t = new Table(doc, new String[]{weekly ? "Week" : "Date", "Orders", "Invoiced", "Collected", "Expenses", "Net"},
                new int[]{1, 2, 3, 4, 5}, new int[]{24, 10, 17, 17, 16, 16});
        List<String[]> rows = new ArrayList<>();
        if (!weekly) {
            data.days().forEach(d -> rows.add(new String[]{DAY.format(d.date()), String.valueOf(d.orders()), money(d.invoiced()),
                    money(d.collected()), money(d.expenses()), money(d.net())}));
        } else {
            List<BranchReportData.Day> days = data.days();
            for (int i = 0; i < days.size(); i += 7) {
                List<BranchReportData.Day> week = days.subList(i, Math.min(days.size(), i + 7));
                LocalDate a = week.get(0).date();
                LocalDate b = week.get(week.size() - 1).date();
                rows.add(new String[]{DAY_SHORT.format(a) + " – " + DAY.format(b),
                        String.valueOf(week.stream().mapToLong(BranchReportData.Day::orders).sum()),
                        money(sum(week.stream().mapToDouble(BranchReportData.Day::invoiced))),
                        money(sum(week.stream().mapToDouble(BranchReportData.Day::collected))),
                        money(sum(week.stream().mapToDouble(BranchReportData.Day::expenses))),
                        money(sum(week.stream().mapToDouble(BranchReportData.Day::net)))});
            }
        }
        t.rows(rows, weekly ? "weeks" : "days");
        Figures c = data.current();
        t.total(new String[]{"Total", String.valueOf(data.days().stream().mapToLong(BranchReportData.Day::orders).sum()),
                money(sum(data.days().stream().mapToDouble(BranchReportData.Day::invoiced))), money(c.collected()),
                money(c.expenses()), money(c.net())});
    }

    private void orders(XWPFDocument doc, BranchReportData data) {
        Table t = new Table(doc, new String[]{"Invoice", "Date", "Customer", "Items", "Total", "Paid", "Balance", "Status"},
                new int[]{3, 4, 5, 6}, new int[]{20, 11, 17, 6, 12, 12, 12, 10});
        List<String[]> rows = new ArrayList<>();
        data.orders().forEach(o -> rows.add(new String[]{o.invoiceNo(), date(o.invoiceDate()), o.customer(), o.itemsDone() + "/" + o.itemsTotal(),
                money(o.total()), money(o.paid()), money(o.balance()), o.paymentStatus() + " · " + o.stage()}));
        t.rows(rows, "orders");
        t.total(new String[]{"Total", "", count(data.orders().size(), "order", "orders"), "",
                money(sum(data.orders().stream().mapToDouble(BranchReportData.Order::total))),
                money(sum(data.orders().stream().mapToDouble(BranchReportData.Order::paid))),
                money(sum(data.orders().stream().mapToDouble(BranchReportData.Order::balance))), ""});
    }

    private void payments(XWPFDocument doc, BranchReportData data) {
        Table t = new Table(doc, new String[]{"Date & time", "Payment receipt", "Invoice", "Customer", "Method", "Amount"},
                new int[]{5}, new int[]{19, 22, 20, 17, 9, 13});
        List<String[]> rows = new ArrayList<>();
        data.payments().forEach(p -> rows.add(new String[]{dateTime(p.paidAt()), nz(p.paymentReceiptNo()), p.invoiceNo(), p.customer(),
                p.method(), money(p.amount())}));
        t.rows(rows, "payments");
        t.total(new String[]{"Total", "", "", count(data.payments().size(), "payment", "payments"), "", money(data.current().collected())});
    }

    private void production(XWPFDocument doc, BranchReportData data) {
        heading(doc, "Production right now", null, true);
        Table t = new Table(doc, new String[]{"Stage", "Items", "Late", "Avg days"}, new int[]{1, 2, 3}, new int[]{46, 18, 18, 18});
        List<String[]> rows = new ArrayList<>();
        data.stagesNow().forEach(s -> rows.add(new String[]{s.stage(), String.valueOf(s.items()), String.valueOf(s.late()),
                s.avgDays() == null ? "—" : String.format(Locale.ENGLISH, "%.1f d", s.avgDays())}));
        t.rows(rows, "stages");
        t.total(new String[]{"Total", String.valueOf(data.stagesNow().stream().mapToLong(BranchReportData.StageNow::items).sum()),
                String.valueOf(data.stagesNow().stream().mapToLong(BranchReportData.StageNow::late).sum()), ""});

        BranchReportData.Turnaround ta = data.turnaround();
        heading(doc, "Where the time goes", ta.delivered() + (ta.delivered() == 1 ? " order delivered" : " orders delivered")
                + (ta.avgDaysToDeliver() == null ? "" : " · average " + ta.avgDaysToDeliver() + " days from order to delivery")
                + (ta.onTimePercent() == null ? "" : " · " + Math.round(ta.onTimePercent()) + "% on time"), false);
        double slowest = data.stageTimes().stream().mapToDouble(BranchReportData.StageTime::avgHours).max().orElse(0);
        t = new Table(doc, new String[]{"Stage", "Average time", ""}, new int[]{1}, new int[]{46, 24, 30});
        rows.clear();
        data.stageTimes().forEach(s -> rows.add(new String[]{s.stage(), duration(s.avgHours()), s.avgHours() == slowest && slowest > 0 ? "Slowest" : ""}));
        t.rows(rows, "delivered orders");
    }

    private void people(XWPFDocument doc, BranchReportData data) {
        heading(doc, "Staff work", null, false);
        Table t = new Table(doc, new String[]{"Name", "Role", "Work in the period", "Last activity"}, new int[]{}, new int[]{16, 15, 47, 22});
        List<String[]> rows = new ArrayList<>();
        data.staff().forEach(s -> rows.add(new String[]{s.name(), s.role(), s.work(), dateTime(s.lastActivity())}));
        t.rows(rows, "staff activity");

        BranchReportData.Customers c = data.customers();
        heading(doc, "Customers", c.active() + " active · " + c.newCustomers() + " new · " + c.returning() + " returning · "
                + String.format(Locale.ENGLISH, "%.1f", c.repeatRate()) + "% repeat rate · average spend " + money0(c.avgSpend()), false);
        t = new Table(doc, new String[]{"#", "Customer", "Phone", "Invoices", "Spent", "Last visit"}, new int[]{3, 4}, new int[]{6, 28, 18, 12, 18, 18});
        rows.clear();
        for (int i = 0; i < data.topCustomers().size(); i++) {
            BranchReportData.TopCustomer tc = data.topCustomers().get(i);
            rows.add(new String[]{String.valueOf(i + 1), tc.name(), nz(tc.phone()), String.valueOf(tc.invoices()), money(tc.spent()), date(tc.lastVisit())});
        }
        t.rows(rows, "customers");

        heading(doc, "Sales by item", null, false);
        t = new Table(doc, new String[]{"Item", "Pieces", "Amount (before GST)"}, new int[]{1, 2}, new int[]{50, 20, 30});
        rows.clear();
        data.itemsSold().forEach(i -> rows.add(new String[]{i.name(), number(i.pieces()), money(i.amount())}));
        t.rows(rows, "items");
        t.total(new String[]{"Total", number(data.itemsSold().stream().mapToDouble(BranchReportData.ItemSold::pieces).sum()),
                money(sum(data.itemsSold().stream().mapToDouble(BranchReportData.ItemSold::amount)))});

        heading(doc, "Deleted invoices", null, false);
        t = new Table(doc, new String[]{"Invoice", "Customer", "Amount", "Deleted on", "By", "Reason"}, new int[]{2}, new int[]{21, 16, 13, 18, 12, 20});
        rows.clear();
        data.deletedInvoices().forEach(d -> rows.add(new String[]{d.invoiceNo(), nz(d.customer()), money(d.amount()), dateTime(d.deletedAt()),
                d.deletedBy(), nz(d.reason())}));
        t.rows(rows, "deleted invoices");
    }

    // ================================================================ table

    /** A full-width table: grey header (repeats on every page), hairline rows, optional total row; right-aligned number columns. */
    private static final class Table {
        private final XWPFTable table;
        private final int[] numeric;
        private final int[] widths;
        private final int columns;

        Table(XWPFDocument doc, String[] headers, int[] numeric, int[] widthsPercent) {
            this.columns = headers.length;
            this.numeric = numeric;
            this.widths = widthsPercent;
            this.table = doc.createTable(1, columns);
            table.setWidth("100%");
            table.setTableAlignment(TableRowAlign.LEFT);
            borders(table, "F2F2F2");
            XWPFTableRow head = table.getRow(0);
            head.setRepeatHeader(true);
            for (int c = 0; c < columns; c++) {
                XWPFTableCell cell = head.getCell(c);
                cell.setColor(HEAD_FILL);
                width(cell, c);
                XWPFParagraph p = cell.getParagraphs().get(0);
                align(p, c);
                spacing(p, 40, 40);
                text(p, headers[c], 7.5, true, "616161");
            }
        }

        /** Data rows (at most MAX_ROWS; then a "+ N more" row). Empty list → "None in this period" (when {@code noun} given). */
        void rows(List<String[]> rows, String noun) {
            if (rows.isEmpty()) {
                XWPFTableRow row = table.createRow();
                XWPFParagraph p = row.getCell(0).getParagraphs().get(0);
                text(p, "None in this period", 8, false, MUTED);
                return;
            }
            int shown = Math.min(rows.size(), MAX_ROWS);
            for (int r = 0; r < shown; r++) {
                XWPFTableRow row = table.createRow();
                row.setCantSplitRow(true);
                String[] values = rows.get(r);
                for (int c = 0; c < columns; c++) {
                    width(row.getCell(c), c);
                    XWPFParagraph p = row.getCell(c).getParagraphs().get(0);
                    align(p, c);
                    spacing(p, 20, 20);
                    text(p, c < values.length ? cellText(values[c]) : "", 8, false, "212121");
                }
            }
            if (rows.size() > shown) {
                XWPFTableRow row = table.createRow();
                XWPFParagraph p = row.getCell(0).getParagraphs().get(0);
                text(p, "+ " + (rows.size() - shown) + " more " + (noun == null ? "rows" : noun) + " — see the Excel report", 8, true, SLATE);
            }
        }

        void total(String[] values) {
            XWPFTableRow row = table.createRow();
            for (int c = 0; c < columns; c++) {
                XWPFTableCell cell = row.getCell(c);
                cell.setColor(TOTAL_FILL);
                width(cell, c);
                XWPFParagraph p = cell.getParagraphs().get(0);
                align(p, c);
                spacing(p, 30, 30);
                text(p, c < values.length ? cellText(values[c]) : "", 8, true, GREEN);
            }
        }

        /** Fixed width in twips on every cell (the PDF converter fails on percentage cell widths). */
        private void width(XWPFTableCell cell, int column) {
            cell.setWidthType(TableWidthType.DXA);
            cell.setWidth(String.valueOf(CONTENT_WIDTH_TWIPS * widths[column] / 100));
        }

        /** null -> "—"; an intentionally empty cell stays empty. */
        private static String cellText(String value) {
            return value == null ? "—" : value;
        }

        private void align(XWPFParagraph p, int column) {
            for (int n : numeric) {
                if (n == column) {
                    p.setAlignment(ParagraphAlignment.RIGHT);
                    return;
                }
            }
        }
    }

    // ================================================================ page, text helpers

    private static void page(XWPFDocument doc) {
        CTSectPr sect = doc.getDocument().getBody().isSetSectPr() ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz size = sect.isSetPgSz() ? sect.getPgSz() : sect.addNewPgSz();
        size.setW(BigInteger.valueOf(11906)); // A4
        size.setH(BigInteger.valueOf(16838));
        CTPageMar margin = sect.isSetPgMar() ? sect.getPgMar() : sect.addNewPgMar();
        margin.setTop(BigInteger.valueOf(794));
        margin.setBottom(BigInteger.valueOf(907));
        margin.setLeft(BigInteger.valueOf(737));
        margin.setRight(BigInteger.valueOf(737));
        margin.setFooter(BigInteger.valueOf(420));
    }

    private static void footer(XWPFDocument doc, BranchReportData data) {
        XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph p = footer.createParagraph();
        BranchReportData.Header h = data.header();
        text(p, "Saritha's Designer Studio · Branch report · " + nz(h.branchName()) + " · "
                + BranchReportExcelWriter.periodText(h.from(), h.to()) + " · generated " + DATE_TIME.format(h.generatedAt())
                + " by " + nz(h.generatedBy()) + " · page ", 7, false, MUTED);
        var field = p.getCTP().addNewFldSimple();
        field.setInstr("PAGE");
        // style the page number like the footer text
        var run = field.addNewR();
        var pr = run.addNewRPr();
        pr.addNewSz().setVal(java.math.BigInteger.valueOf(14));
        pr.addNewColor().setVal(MUTED);
        run.addNewT().setStringValue("1");
    }

    private static void heading(XWPFDocument doc, String title, String note, boolean newPage) {
        XWPFParagraph p = doc.createParagraph();
        p.setPageBreak(newPage);
        spacing(p, newPage ? 0 : 280, 100);
        p.setKeepNext(true);
        text(p, title, 12, true, INK);
        if (note != null && !note.isBlank()) {
            text(p, "   " + note, 8, false, SLATE);
        }
    }

    private static XWPFRun text(XWPFParagraph p, String value, double size, boolean bold, String color) {
        XWPFRun run = p.createRun();
        run.setText(value);
        run.setFontFamily(FONT);
        run.setFontSize(size);
        run.setBold(bold);
        run.setColor(color);
        return run;
    }

    private static void spacing(XWPFParagraph p, int before, int after) {
        p.setSpacingBefore(before);
        p.setSpacingAfter(after);
    }

    private static void borders(XWPFTable table, String color) {
        CTTblPr pr = table.getCTTbl().getTblPr();
        CTTblBorders b = pr.isSetTblBorders() ? pr.getTblBorders() : pr.addNewTblBorders();
        for (var border : new org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder[]{
                b.isSetTop() ? b.getTop() : b.addNewTop(), b.isSetBottom() ? b.getBottom() : b.addNewBottom(),
                b.isSetInsideH() ? b.getInsideH() : b.addNewInsideH()}) {
            border.setVal(STBorder.SINGLE);
            border.setSz(BigInteger.valueOf(4));
            border.setColor(color);
        }
        for (var border : new org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder[]{
                b.isSetLeft() ? b.getLeft() : b.addNewLeft(), b.isSetRight() ? b.getRight() : b.addNewRight(),
                b.isSetInsideV() ? b.getInsideV() : b.addNewInsideV()}) {
            border.setVal(STBorder.NIL);
        }
    }

    private static void noBorders(XWPFTable table) {
        CTTblPr pr = table.getCTTbl().getTblPr();
        CTTblBorders b = pr.isSetTblBorders() ? pr.getTblBorders() : pr.addNewTblBorders();
        for (var border : new org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder[]{
                b.isSetTop() ? b.getTop() : b.addNewTop(), b.isSetBottom() ? b.getBottom() : b.addNewBottom(),
                b.isSetLeft() ? b.getLeft() : b.addNewLeft(), b.isSetRight() ? b.getRight() : b.addNewRight(),
                b.isSetInsideH() ? b.getInsideH() : b.addNewInsideH(), b.isSetInsideV() ? b.getInsideV() : b.addNewInsideV()}) {
            border.setVal(STBorder.NIL);
        }
    }

    // ================================================================ formatting

    /** ₹1,23,456.78 (Indian grouping). */
    static String money(double value) {
        long paise = Math.round(Math.abs(value) * 100);
        return (value < 0 ? "-" : "") + "₹" + group(paise / 100) + String.format(Locale.ENGLISH, ".%02d", paise % 100);
    }

    /** ₹1,23,457 (no paise, for the summary figures). */
    static String money0(double value) {
        return (value < 0 ? "-" : "") + "₹" + group(Math.round(Math.abs(value)));
    }

    private static String group(long whole) {
        String s = Long.toString(whole);
        if (s.length() <= 3) {
            return s;
        }
        String last3 = s.substring(s.length() - 3);
        String head = s.substring(0, s.length() - 3);
        StringBuilder out = new StringBuilder();
        while (head.length() > 2) {
            out.insert(0, "," + head.substring(head.length() - 2));
            head = head.substring(0, head.length() - 2);
        }
        return head + out + "," + last3;
    }

    /** "+13.6%" when better, "-5.7%" when worse ("better" = up, or down for costs); "" without a previous figure. */
    private static String change(double current, double previous, boolean upIsGood) {
        if (previous == 0) {
            return "";
        }
        double pct = (current - previous) / Math.abs(previous) * 100;
        boolean good = upIsGood ? pct >= 0 : pct <= 0;
        return (good ? "+" : "-") + (pct >= 0 ? "▲ " : "▼ ") + String.format(Locale.ENGLISH, "%.1f%%", Math.abs(pct));
    }

    private static String duration(double hours) {
        return hours >= 24 ? String.format(Locale.ENGLISH, "%.1f days", hours / 24) : Math.round(hours) + " h";
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ENGLISH, "%.2f", value);
    }

    private static String date(Date value) {
        return value == null ? "—" : DAY.format(LocalDateTime.ofInstant(value.toInstant(), IST));
    }

    private static String dateTime(Date value) {
        return value == null ? "—" : DATE_TIME.format(LocalDateTime.ofInstant(value.toInstant(), IST)).replace("AM", "am").replace("PM", "pm");
    }

    private static double sum(java.util.stream.DoubleStream values) {
        return Math.round(values.sum() * 100.0) / 100.0;
    }

    private static String count(long n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    private static String nz(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
