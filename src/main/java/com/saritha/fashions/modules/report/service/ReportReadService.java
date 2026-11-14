package com.saritha.fashions.modules.report.service;

import com.saritha.fashions.modules.report.payload.ActivityDayResponse;
import com.saritha.fashions.modules.report.payload.ActivityResponse;
import com.saritha.fashions.modules.report.payload.DailyFinancialsResponse;
import com.saritha.fashions.modules.report.payload.DeletedInvoiceResponse;
import com.saritha.fashions.modules.report.payload.EmployeeActivitySummaryResponse;
import com.saritha.fashions.modules.report.payload.FinancialSummaryResponse;
import com.saritha.fashions.modules.report.payload.MyActivityResponse;
import com.saritha.fashions.modules.report.payload.ShopOverviewResponse;
import com.saritha.fashions.payload.PaginatedResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Reports (read-only).
 *
 * Shop scope: a SHOP_OWNER always gets their own shop (shopId is ignored); the platform OWNER
 * gets the shop named by shopId, or all shops when shopId is left out.
 * Periods: from / to are inclusive IST days; null means open-ended unless stated otherwise.
 */
public interface ReportReadService {

    /** Platform OWNER only: one row per shop. */
    List<ShopOverviewResponse> getShopsOverview(LocalDate from, LocalDate to);

    FinancialSummaryResponse getFinancialSummary(String shopId, LocalDate from, LocalDate to);

    /** One row per day; default the last 30 days, at most 366 days. */
    List<DailyFinancialsResponse> getDailyFinancials(String shopId, LocalDate from, LocalDate to);

    PaginatedResponse<ActivityResponse> listActivities(String shopId, String employeeId, String type,
                                                       LocalDate from, LocalDate to, int pageNumber, int pageSize);

    /** Per-employee counts, most active first. */
    List<EmployeeActivitySummaryResponse> getActivityByEmployee(String shopId, LocalDate from, LocalDate to);

    /** Per-day counts for the shop, or one employee of it; default the last 30 days. */
    List<ActivityDayResponse> getActivityDaily(String shopId, String employeeId, LocalDate from, LocalDate to);

    /** The logged-in user's own activity; default the last 30 days. */
    MyActivityResponse getMyActivity(LocalDate from, LocalDate to);

    PaginatedResponse<DeletedInvoiceResponse> listDeletedInvoices(String shopId, String search, LocalDate from,
                                                                  LocalDate to, int pageNumber, int pageSize);

    /**
     * Delivery dashboard for a day (default today, IST): orders due that day (ready / in production),
     * overdue orders and orders delivered that day.
     */
    com.saritha.fashions.modules.report.payload.DeliveryDashboardResponse getDeliveries(String shopId, LocalDate date);
}
