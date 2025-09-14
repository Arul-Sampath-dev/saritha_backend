package com.saritha.fashions.controller.Activity;

import com.saritha.fashions.config.AppConstants;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.activity.DailyCountDTO;
import com.saritha.fashions.payload.activity.EmployeeActivitySummaryDTO;
import com.saritha.fashions.service.Interface.EmployeeActivityService;
import com.saritha.fashions.util.DateTimeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/v1/api/employee/activities")
public class EmployeeActivityController {

    @Autowired
    private EmployeeActivityService activityService;

    // Paginated per-employee daily summary
    @GetMapping("/summary")
    public ResponseEntity<PaginatedResponse<EmployeeActivitySummaryDTO>> getActivitySummary(
            @RequestParam String shopId,
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE) Integer pageSize,
            @RequestParam(defaultValue = "employeeName") String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate
    ) {
        if (endDate != null) {
            endDate = DateTimeUtils.endOfDayInIST(endDate);
        }
        return ResponseEntity.ok(
                activityService.getEmployeeDailySummary(shopId, startDate, endDate, pageNumber, pageSize, sortBy, sortOrder)
        );
    }

    @GetMapping("/employee/{employeeId}/daily")
    public ResponseEntity<PaginatedResponse<DailyCountDTO>> getEmployeeDaily(
            @RequestParam String shopId,
            @PathVariable String employeeId,
            @RequestParam(defaultValue = AppConstants.PAGE_NUMBER) Integer pageNumber,
            @RequestParam(defaultValue = AppConstants.PAGE_SIZE) Integer pageSize,
            @RequestParam(defaultValue = "_id") String sortBy,
            @RequestParam(defaultValue = AppConstants.SORT_DIR) String sortOrder,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate
    ) {
        if (endDate != null) {
            endDate = DateTimeUtils.endOfDayInIST(endDate);
        }
        return ResponseEntity.ok(
                activityService.getEmployeeDailyBreakdown(shopId, employeeId, startDate, endDate, pageNumber, pageSize, sortBy, sortOrder)
        );
    }

}

