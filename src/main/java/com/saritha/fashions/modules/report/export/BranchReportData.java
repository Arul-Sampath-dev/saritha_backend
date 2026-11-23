package com.saritha.fashions.modules.report.export;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * Everything the branch report shows, collected once and written by both {@link BranchReportExcelWriter} and
 * {@link BranchReportPdfWriter} (so the Excel and the PDF always show the same numbers). Values are typed (numbers,
 * dates); the writers format them. Dates are IST.
 */
public record BranchReportData(
        Header header,
        Figures current,
        Figures previous,
        List<GstRow> gst,
        List<Amount> collectedByMethod,
        List<Amount> expensesByCategory,
        List<Day> days,
        List<Order> orders,
        List<Payment> payments,
        List<Delivery> deliveries,
        List<Expense> expenses,
        List<DeletedExpense> deletedExpenses,
        List<TopUp> topUps,
        List<StageNow> stagesNow,
        Turnaround turnaround,
        List<StageTime> stageTimes,
        List<Staff> staff,
        Customers customers,
        List<TopCustomer> topCustomers,
        List<DeletedInvoice> deletedInvoices,
        List<ItemSold> itemsSold) {

    public record Header(String shopId, String branchName, String address, String phone, String gstin, String ownerName,
                         LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo,
                         LocalDateTime generatedAt, String generatedBy) {

        public long days() {
            return java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        }
    }

    /** The money figures of a period (current or previous). */
    public record Figures(long orders, double sales, double taxable, double gst, double discount,
                          double collected, long payments, double expenses, long expenseEntries,
                          double shopExpenses, double employeeExpenses, double net, long delivered,
                          long openInvoices, double outstanding, double pettyAdded, double pettyBalance,
                          long deletedInvoices) {
    }

    public record GstRow(String label, long invoices, double taxable, double cgst, double sgst, double igst, double total) {
    }

    public record Amount(String name, long count, double amount) {
    }

    public record Day(LocalDate date, long orders, double invoiced, double collected, double expenses, double net) {
    }

    public record Order(String invoiceNo, Date invoiceDate, String customer, String phone, int itemsDone, int itemsTotal,
                       String items, double taxable, double gst, double total, double paid, double balance,
                       String paymentStatus, String stage, String dueDate, Date deliveredAt) {
    }

    public record Payment(Date paidAt, String paymentReceiptNo, String invoiceNo, String customer, String method,
                         String transactionId, double amount, String receivedBy) {
    }

    public record Delivery(String receiptNo, Date deliveredAt, String invoiceNo, String customer, double total, String method) {
    }

    public record Expense(Date date, String category, String forWhom, String method, String notes, double amount) {
    }

    public record DeletedExpense(Date date, String category, double amount, Date deletedAt, String deletedBy, String reason) {
    }

    public record TopUp(Date addedAt, String addedBy, double amount) {
    }

    public record StageNow(String stage, long items, long late, Double avgDays) {
    }

    public record Turnaround(long delivered, Double avgDaysToDeliver, Double onTimePercent, long withDueDate) {
    }

    public record StageTime(String stage, double avgHours, long samples) {
    }

    public record Staff(String name, String role, String work, long actions, Date lastActivity) {
    }

    public record Customers(long active, long newCustomers, long returning, double repeatRate, double avgSpend) {
    }

    public record TopCustomer(String name, String phone, long invoices, double spent, Date lastVisit) {
    }

    public record DeletedInvoice(String invoiceNo, Date invoiceDate, String customer, double amount, Date deletedAt,
                                 String deletedBy, String reason) {
    }

    public record ItemSold(String name, double pieces, double amount) {
    }
}
