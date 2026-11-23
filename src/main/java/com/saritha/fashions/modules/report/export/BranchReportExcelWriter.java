package com.saritha.fashions.modules.report.export;

import com.saritha.fashions.modules.report.export.BranchReportData.Figures;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Writes the branch report as an Excel workbook (one sheet per section, like report-samples/*.xlsx).
 * Numbers are real numbers (₹ with Indian grouping), dates real dates; every list has a frozen header row,
 * an auto-filter and a SUM totals row. Streaming (SXSSF), so long periods stay light on memory.
 */
@Component
public class BranchReportExcelWriter {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** ₹ with Indian grouping: 1,23,45,678.00. */
    static final String INR = "[>=10000000]\"₹\"##\\,##\\,##\\,##0.00;[>=100000]\"₹\"##\\,##\\,##0.00;\"₹\"##,##0.00";
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("d MMM yyyy");

    /** Column kinds. */
    enum K { TEXT, INT, MONEY, DATE, DATETIME, PCT, DECIMAL, WRAP }

    public byte[] write(BranchReportData data) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(workbook);
            String sub = data.header().branchName() + " · " + periodText(data.header().from(), data.header().to());

            summary(workbook, st, data, sub);

            Sheet daily = sheet(workbook, st, "Daily", "Day by day", sub);
            daily.table(List.of(col("Date", K.DATE, 14), col("Orders", K.INT, 10), col("Invoiced", K.MONEY, 16),
                            col("Collected", K.MONEY, 16), col("Expenses", K.MONEY, 16), col("Net", K.MONEY, 16)),
                    data.days().stream().map(d -> List.<Object>of(d.date(), d.orders(), d.invoiced(), d.collected(), d.expenses(), d.net())).toList(),
                    List.of(1, 2, 3, 4, 5), true, null);

            Sheet orders = sheet(workbook, st, "Orders", "Orders (invoices dated in the period)", sub);
            orders.table(List.of(col("Invoice no", K.TEXT, 24), col("Date", K.DATE, 13), col("Customer", K.TEXT, 20),
                            col("Phone", K.TEXT, 13), col("Items done", K.TEXT, 10), col("Items", K.WRAP, 40), col("Taxable", K.MONEY, 14),
                            col("GST", K.MONEY, 12), col("Total", K.MONEY, 14), col("Paid", K.MONEY, 14), col("Balance", K.MONEY, 14),
                            col("Payment", K.TEXT, 10), col("Stage", K.TEXT, 14), col("Due date", K.TEXT, 13), col("Delivered on", K.DATETIME, 20)),
                    data.orders().stream().map(o -> list(o.invoiceNo(), o.invoiceDate(), o.customer(), o.phone(),
                            o.itemsDone() + "/" + o.itemsTotal(), o.items(), o.taxable(), o.gst(), o.total(), o.paid(), o.balance(),
                            o.paymentStatus(), o.stage(), o.dueDate(), o.deliveredAt())).toList(),
                    List.of(6, 7, 8, 9, 10), true, null);

            Sheet payments = sheet(workbook, st, "Payments", "Payments received", sub);
            payments.table(List.of(col("Date & time", K.DATETIME, 20), col("Payment receipt", K.TEXT, 26), col("Invoice no", K.TEXT, 24),
                            col("Customer", K.TEXT, 20), col("Method", K.TEXT, 12), col("Transaction id", K.TEXT, 18),
                            col("Amount", K.MONEY, 14), col("Received by", K.TEXT, 16)),
                    data.payments().stream().map(p -> list(p.paidAt(), p.paymentReceiptNo(), p.invoiceNo(), p.customer(), p.method(),
                            p.transactionId(), p.amount(), p.receivedBy())).toList(),
                    List.of(6), true, null);

            Sheet deliveries = sheet(workbook, st, "Deliveries", "Deliveries (final receipts)", sub);
            deliveries.table(List.of(col("Receipt no", K.TEXT, 24), col("Delivered on", K.DATETIME, 20), col("Invoice no", K.TEXT, 24),
                            col("Customer", K.TEXT, 20), col("Total", K.MONEY, 14), col("Method", K.TEXT, 12)),
                    data.deliveries().stream().map(x -> list(x.receiptNo(), x.deliveredAt(), x.invoiceNo(), x.customer(), x.total(), x.method())).toList(),
                    List.of(4), true, null);

            Sheet expenses = sheet(workbook, st, "Expenses", "Expenses", sub);
            expenses.table(List.of(col("Date", K.DATE, 13), col("Category", K.TEXT, 18), col("For", K.TEXT, 16), col("Method", K.TEXT, 14),
                            col("Notes", K.WRAP, 34), col("Amount", K.MONEY, 14)),
                    data.expenses().stream().map(x -> list(x.date(), x.category(), x.forWhom(), x.method(), x.notes(), x.amount())).toList(),
                    List.of(5), true, null);
            expenses.table(List.of(col("Date", K.DATE, 13), col("Category", K.TEXT, 18), col("Amount", K.MONEY, 16), col("Deleted on", K.DATETIME, 14),
                            col("Deleted by", K.TEXT, 34), col("Reason", K.TEXT, 14)),
                    data.deletedExpenses().stream().map(x -> list(x.date(), x.category(), x.amount(), x.deletedAt(), x.deletedBy(), x.reason())).toList(),
                    List.of(2), false, "Deleted expenses");

            Sheet petty = sheet(workbook, st, "Petty cash", "Petty cash", sub);
            petty.table(List.of(col("Added on", K.DATETIME, 20), col("Added by", K.TEXT, 20), col("Amount", K.MONEY, 16)),
                    data.topUps().stream().map(t -> list(t.addedAt(), t.addedBy(), t.amount())).toList(), List.of(2), true, null);
            petty.keyValue("Balance now", data.current().pettyBalance(), K.MONEY);

            Sheet production = sheet(workbook, st, "Production", "Production", sub);
            production.table(List.of(col("Stage (right now)", K.TEXT, 30), col("Items", K.INT, 10), col("Late", K.INT, 10), col("Avg days in stage", K.DECIMAL, 18)),
                    data.stagesNow().stream().map(s -> list(s.stage(), s.items(), s.late(), s.avgDays())).toList(), List.of(1, 2), false, null);
            production.keyValue("Orders delivered", data.turnaround().delivered(), K.INT);
            production.keyValue("Average days order → delivery", data.turnaround().avgDaysToDeliver(), K.DECIMAL);
            production.keyValue("On time %", data.turnaround().onTimePercent(), K.PCT);
            production.gap();
            production.table(List.of(col("Stage (delivered orders)", K.TEXT, 30), col("Average hours", K.DECIMAL, 14), col("Average days", K.DECIMAL, 14),
                            col("Item stays measured", K.INT, 18)),
                    data.stageTimes().stream().map(t -> list(t.stage(), round1(t.avgHours()), round1(t.avgHours() / 24), t.samples())).toList(),
                    List.of(), false, "Where the time goes");

            Sheet staff = sheet(workbook, st, "Staff", "Staff work", sub);
            staff.table(List.of(col("Name", K.TEXT, 18), col("Role", K.TEXT, 16), col("Work in the period", K.WRAP, 60),
                            col("Actions", K.INT, 10), col("Last activity", K.DATETIME, 20)),
                    data.staff().stream().map(s -> list(s.name(), s.role(), s.work(), s.actions(), s.lastActivity())).toList(),
                    List.of(), true, null);

            Sheet customers = sheet(workbook, st, "Customers", "Customers", sub);
            customers.keyValue("Active customers", data.customers().active(), K.INT);
            customers.keyValue("New", data.customers().newCustomers(), K.INT);
            customers.keyValue("Returning", data.customers().returning(), K.INT);
            customers.keyValue("Repeat rate", data.customers().repeatRate(), K.PCT);
            customers.keyValue("Average spend", data.customers().avgSpend(), K.MONEY);
            customers.gap();
            customers.table(List.of(col("#", K.INT, 18), col("Customer", K.TEXT, 22), col("Phone", K.TEXT, 14), col("Invoices", K.INT, 10),
                            col("Spent", K.MONEY, 14), col("Last visit", K.DATE, 13)),
                    indexed(data.topCustomers().stream().map(c -> list(c.name(), c.phone(), c.invoices(), c.spent(), c.lastVisit())).toList()),
                    List.of(), false, "Top 10 customers");

            Sheet deleted = sheet(workbook, st, "Deleted invoices", "Deleted invoices", sub);
            deleted.table(List.of(col("Invoice no", K.TEXT, 24), col("Date", K.DATE, 13), col("Customer", K.TEXT, 18), col("Amount", K.MONEY, 14),
                            col("Deleted on", K.DATETIME, 20), col("Deleted by", K.TEXT, 14), col("Reason", K.WRAP, 30)),
                    data.deletedInvoices().stream().map(d -> list(d.invoiceNo(), d.invoiceDate(), d.customer(), d.amount(), d.deletedAt(),
                            d.deletedBy(), d.reason())).toList(),
                    List.of(3), true, null);

            Sheet items = sheet(workbook, st, "Items", "Sales by item", sub);
            items.table(List.of(col("Item", K.TEXT, 24), col("Pieces", K.DECIMAL, 10), col("Amount (before GST)", K.MONEY, 20)),
                    data.itemsSold().stream().map(i -> list(i.name(), i.pieces(), i.amount())).toList(), List.of(1, 2), true, null);

            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write the Excel report", exception);
        }
    }

    // ================================================================ summary sheet

    private void summary(SXSSFWorkbook workbook, Styles st, BranchReportData data, String sub) {
        BranchReportData.Header h = data.header();
        Sheet s = sheet(workbook, st, "Summary", "Branch report", h.branchName());
        s.keyValue("Address", h.address(), K.TEXT);
        s.keyValue("Phone", h.phone(), K.TEXT);
        s.keyValue("GSTIN", h.gstin(), K.TEXT);
        s.keyValue("Shop owner", h.ownerName(), K.TEXT);
        s.keyValue("Period", periodText(h.from(), h.to()), K.TEXT);
        s.keyValue("Compared with", periodText(h.previousFrom(), h.previousTo()), K.TEXT);
        s.keyValue("Generated on", h.generatedAt(), K.DATETIME);
        s.keyValue("Generated by", h.generatedBy(), K.TEXT);
        s.gap();

        Figures c = data.current();
        Figures p = data.previous();
        List<List<Object>> rows = new ArrayList<>();
        rows.add(figure("Orders (invoices)", c.orders(), p.orders()));
        rows.add(figure("Sales (invoiced, incl. GST)", c.sales(), p.sales()));
        rows.add(figure("Taxable value", c.taxable(), p.taxable()));
        rows.add(figure("GST", c.gst(), p.gst()));
        rows.add(figure("Discount", c.discount(), p.discount()));
        rows.add(figure("Collected", c.collected(), p.collected()));
        rows.add(figure("Expenses", c.expenses(), p.expenses()));
        rows.add(figure("Net cash flow (collected − expenses)", c.net(), p.net()));
        rows.add(figure("Orders delivered", c.delivered(), p.delivered()));
        rows.add(figure("Petty cash added", c.pettyAdded(), p.pettyAdded()));
        rows.add(figure("Deleted invoices", c.deletedInvoices(), p.deletedInvoices()));
        int first = s.table(List.of(col("Figure", K.TEXT, 38), col("This period", K.MONEY, 20), col("Previous period", K.MONEY, 20),
                col("Change %", K.PCT, 12)), rows, List.of(), false, "Summary");
        // count rows: integers, not money
        for (int i : new int[]{0, 8, 10}) {
            s.restyle(first + i, 1, st.integer);
            s.restyle(first + i, 2, st.integer);
        }
        s.keyValue("Outstanding now (open invoices)", c.outstanding(), K.MONEY);
        s.keyValue("Open invoices now", c.openInvoices(), K.INT);
        s.keyValue("Petty cash balance now", c.pettyBalance(), K.MONEY);
        s.gap();

        s.table(List.of(col("Type", K.TEXT, 38), col("Invoices", K.INT, 20), col("Taxable value", K.MONEY, 20), col("CGST", K.MONEY, 14),
                        col("SGST", K.MONEY, 14), col("IGST", K.MONEY, 14), col("Total", K.MONEY, 16)),
                data.gst().stream().map(g -> list(g.label(), g.invoices(), g.taxable(), g.cgst(), g.sgst(), g.igst(), g.total())).toList(),
                List.of(1, 2, 3, 4, 5, 6), false, "GST summary (business summary — not a GST return)");
        s.table(List.of(col("Payment method", K.TEXT, 38), col("Payments", K.INT, 20), col("Amount", K.MONEY, 20)),
                data.collectedByMethod().stream().map(m -> list(m.name(), m.count(), m.amount())).toList(), List.of(1, 2), false,
                "Collected by payment method");
        s.table(List.of(col("Expense category", K.TEXT, 38), col("Entries", K.INT, 20), col("Amount", K.MONEY, 20)),
                data.expensesByCategory().stream().map(m -> list(m.name(), m.count(), m.amount())).toList(), List.of(1, 2), false,
                "Expenses by category");
    }

    private static List<Object> figure(String name, double current, double previous) {
        Double change = previous == 0 ? null : Math.round((current - previous) / Math.abs(previous) * 1000.0) / 10.0;
        return list(name, current, previous, change);
    }

    // ================================================================ sheet helper

    record Col(String title, K kind, int width) {
    }

    private static Col col(String title, K kind, int width) {
        return new Col(title, kind, width);
    }

    private static List<Object> list(Object... values) {
        List<Object> list = new ArrayList<>(values.length);
        java.util.Collections.addAll(list, values);
        return list;
    }

    private static List<List<Object>> indexed(List<List<Object>> rows) {
        List<List<Object>> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = new ArrayList<>();
            row.add(i + 1);
            row.addAll(rows.get(i));
            out.add(row);
        }
        return out;
    }

    private static Sheet sheet(SXSSFWorkbook workbook, Styles st, String name, String title, String sub) {
        SXSSFSheet sheet = workbook.createSheet(name);
        sheet.setDisplayGridlines(false);
        sheet.getPrintSetup().setLandscape(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        Sheet s = new Sheet(sheet, st);
        s.cell(s.newRow(), 0, title, st.title);
        s.cell(s.newRow(), 0, sub, st.sub);
        s.gap();
        return s;
    }

    /** Rows written top to bottom. */
    private static final class Sheet {
        private final SXSSFSheet sheet;
        private final Styles st;
        private int next;
        private boolean frozen;

        Sheet(SXSSFSheet sheet, Styles st) {
            this.sheet = sheet;
            this.st = st;
        }

        Row newRow() {
            return sheet.createRow(next++);
        }

        void gap() {
            next++;
        }

        void cell(Row row, int column, Object value, CellStyle style) {
            Cell cell = row.createCell(column);
            set(cell, value);
            if (style != null) {
                cell.setCellStyle(style);
            }
        }

        void keyValue(String key, Object value, K kind) {
            Row row = newRow();
            cell(row, 0, key, st.key);
            cell(row, 1, value, st.style(kind, false));
            if (sheet.getColumnWidth(0) < 38 * 256) {
                sheet.setColumnWidth(0, 38 * 256);
            }
            if (sheet.getColumnWidth(1) < 22 * 256) {
                sheet.setColumnWidth(1, 22 * 256);
            }
        }

        void restyle(int rowIndex, int column, CellStyle style) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && row.getCell(column) != null) {
                row.getCell(column).setCellStyle(style);
            }
        }

        /**
         * A table: optional title, header row, rows, optional SUM totals row. The first table of a sheet with
         * {@code freeze} gets the frozen header and the auto-filter. Returns the index of the first data row.
         */
        int table(List<Col> cols, List<List<Object>> rows, List<Integer> totals, boolean freeze, String title) {
            if (title != null) {
                cell(newRow(), 0, title, st.tableTitle);
            }
            Row header = newRow();
            for (int c = 0; c < cols.size(); c++) {
                Col col = cols.get(c);
                cell(header, c, col.title(), numeric(col.kind()) ? st.headerRight : st.header);
                if (sheet.getColumnWidth(c) < col.width() * 256) {
                    sheet.setColumnWidth(c, col.width() * 256);
                }
            }
            int headerIndex = header.getRowNum();
            int firstData = next;
            if (rows.isEmpty()) {
                cell(newRow(), 0, "None in this period", st.none);
                gap();
                return firstData;
            }
            for (List<Object> values : rows) {
                Row row = newRow();
                for (int c = 0; c < cols.size(); c++) {
                    cell(row, c, c < values.size() ? values.get(c) : null, st.style(cols.get(c).kind(), false));
                }
            }
            int lastData = next - 1;
            if (!totals.isEmpty()) {
                Row total = newRow();
                for (int c = 0; c < cols.size(); c++) {
                    Cell cell = total.createCell(c);
                    if (c == 0) {
                        cell.setCellValue("Total");
                        cell.setCellStyle(st.totalLabel);
                    } else if (totals.contains(c)) {
                        String letter = CellReference.convertNumToColString(c);
                        cell.setCellFormula("SUM(" + letter + (firstData + 1) + ":" + letter + (lastData + 1) + ")");
                        cell.setCellStyle(st.style(cols.get(c).kind(), true));
                    } else {
                        cell.setCellStyle(st.totalLabel);
                    }
                }
            }
            if (freeze && !frozen) {
                sheet.createFreezePane(0, headerIndex + 1);
                sheet.setAutoFilter(new CellRangeAddress(headerIndex, lastData, 0, cols.size() - 1));
                frozen = true;
            }
            gap();
            return firstData;
        }

        private static void set(Cell cell, Object value) {
            if (value == null) {
                cell.setBlank();
            } else if (value instanceof Number n) {
                cell.setCellValue(n.doubleValue());
            } else if (value instanceof Date d) {
                cell.setCellValue(LocalDateTime.ofInstant(d.toInstant(), IST));
            } else if (value instanceof LocalDateTime t) {
                cell.setCellValue(t);
            } else if (value instanceof LocalDate d) {
                cell.setCellValue(d);
            } else {
                cell.setCellValue(value.toString());
            }
        }
    }

    private static boolean numeric(K kind) {
        return kind == K.INT || kind == K.MONEY || kind == K.PCT || kind == K.DECIMAL;
    }

    private static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    static String periodText(LocalDate from, LocalDate to) {
        return from.equals(to) ? PERIOD.format(from) : PERIOD.format(from) + " – " + PERIOD.format(to);
    }

    // ================================================================ styles

    private static final class Styles {
        final CellStyle title, sub, key, tableTitle, header, headerRight, none, totalLabel;
        final CellStyle text, wrap, integer, money, date, dateTime, pct, decimal;
        final CellStyle totalInt, totalMoney, totalDecimal;

        Styles(SXSSFWorkbook wb) {
            DataFormat df = wb.createDataFormat();
            XSSFColor ink = rgb(0x17, 0x17, 0x1c);
            XSSFColor slate = rgb(0x75, 0x75, 0x8a);
            XSSFColor green = rgb(0x00, 0x3c, 0x33);

            title = wb.createCellStyle();
            title.setFont(font(wb, 14, true, ink));
            sub = wb.createCellStyle();
            sub.setFont(font(wb, 10, false, slate));
            key = wb.createCellStyle();
            key.setFont(font(wb, 10, false, slate));
            tableTitle = wb.createCellStyle();
            tableTitle.setFont(font(wb, 11, true, green));

            header = headerStyle(wb, ink, HorizontalAlignment.LEFT);
            headerRight = headerStyle(wb, ink, HorizontalAlignment.RIGHT);
            none = wb.createCellStyle();
            XSSFFont italic = font(wb, 10, false, slate);
            italic.setItalic(true);
            none.setFont(italic);

            text = body(wb, null, HorizontalAlignment.LEFT, df);
            wrap = body(wb, null, HorizontalAlignment.LEFT, df);
            wrap.setWrapText(true);
            integer = body(wb, "#,##0", HorizontalAlignment.RIGHT, df);
            money = body(wb, INR, HorizontalAlignment.RIGHT, df);
            date = body(wb, "dd-mmm-yyyy", HorizontalAlignment.LEFT, df);
            dateTime = body(wb, "dd-mmm-yyyy h:mm AM/PM", HorizontalAlignment.LEFT, df);
            pct = body(wb, "0.0\"%\"", HorizontalAlignment.RIGHT, df);
            decimal = body(wb, "#,##0.0", HorizontalAlignment.RIGHT, df);

            totalLabel = total(wb, null, HorizontalAlignment.LEFT, df, green);
            totalInt = total(wb, "#,##0", HorizontalAlignment.RIGHT, df, green);
            totalMoney = total(wb, INR, HorizontalAlignment.RIGHT, df, green);
            totalDecimal = total(wb, "#,##0.0", HorizontalAlignment.RIGHT, df, green);
        }

        CellStyle style(K kind, boolean totalRow) {
            if (totalRow) {
                return switch (kind) {
                    case MONEY -> totalMoney;
                    case DECIMAL -> totalDecimal;
                    default -> totalInt;
                };
            }
            return switch (kind) {
                case INT -> integer;
                case MONEY -> money;
                case DATE -> date;
                case DATETIME -> dateTime;
                case PCT -> pct;
                case DECIMAL -> decimal;
                case WRAP -> wrap;
                default -> text;
            };
        }

        private static XSSFColor rgb(int r, int g, int b) {
            return new XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, null);
        }

        private static XSSFFont font(SXSSFWorkbook wb, int size, boolean bold, XSSFColor color) {
            XSSFFont font = (XSSFFont) wb.createFont();
            font.setFontName("Calibri");
            font.setFontHeightInPoints((short) size);
            font.setBold(bold);
            font.setColor(color);
            return font;
        }

        private static CellStyle headerStyle(SXSSFWorkbook wb, XSSFColor ink, HorizontalAlignment align) {
            XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
            style.setFont(font(wb, 10, true, ink));
            style.setFillForegroundColor(rgb(0xf4, 0xf4, 0xf6));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBottomBorderColor(rgb(0xe5, 0xe7, 0xeb));
            style.setAlignment(align);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private static CellStyle body(SXSSFWorkbook wb, String format, HorizontalAlignment align, DataFormat df) {
            XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
            if (format != null) {
                style.setDataFormat(df.getFormat(format));
            }
            style.setAlignment(align);
            style.setVerticalAlignment(VerticalAlignment.TOP);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBottomBorderColor(rgb(0xf2, 0xf2, 0xf2));
            return style;
        }

        private static CellStyle total(SXSSFWorkbook wb, String format, HorizontalAlignment align, DataFormat df, XSSFColor green) {
            XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
            if (format != null) {
                style.setDataFormat(df.getFormat(format));
            }
            style.setFont(font(wb, 10, true, green));
            style.setFillForegroundColor(rgb(0xed, 0xfc, 0xe9));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(align);
            return style;
        }
    }
}
