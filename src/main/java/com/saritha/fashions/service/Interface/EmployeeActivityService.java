package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.ActivityType;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.activity.DailyCountDTO;
import com.saritha.fashions.payload.activity.EmployeeActivitySummaryDTO;

import java.util.Date;
import java.util.Map;

public interface EmployeeActivityService {

    void recordActivity(String shopId,
                               String invoiceId,
                               String invoiceDbId,
                               String employeeId,
                               String employeeName,
                               ActivityType type,
                               String notes);

    void recordActivity(
                        String shopId,
                        String invoiceId,
                        String invoiceDbId,
                        String employeeId,
                        String employeeName,
                        ActivityType type,
                        String notes,
                        int itemQuantity,
                        double itemPrice,
                        int reassignmentCount,
                        String assignedTo
    );

    void recordActivity(
                        String shopId,
                        String invoiceId,
                        String invoiceDbId,
                        String employeeId,
                        String employeeName,
                        ActivityType type,
                        String notes,
                        int itemQuantity,
                        double itemPrice,
                        int reassignmentCount
    );

    PaginatedResponse<EmployeeActivitySummaryDTO> getEmployeeDailySummary(
            String shopId,
            Date fromInclusive,
            Date toInclusive,
            int pageNumber,
            int pageSize,
            String sortBy,
            String sortOrder
    );

    PaginatedResponse<DailyCountDTO> getEmployeeDailyBreakdown(
            String shopId,
            String employeeId,
            Date fromInclusive,
            Date toInclusive,
            int pageNumber,
            int pageSize,
            String sortBy,
            String sortOrder
    );

    Map<String, Object> getEmployeeAnalytics(String shopId, String employeeId, Date from, Date to);

    Map<String, Object> getAnalyticsForRole(String shopId, String employeeId, String role, Date from, Date to);
}
