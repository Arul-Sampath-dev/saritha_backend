package com.saritha.fashions.modules.employee.service;

import com.saritha.fashions.modules.employee.payload.EmployeeResponse;

import java.util.List;

/**
 * Read side of employee management.
 */
public interface EmployeeReadService {

    /**
     * Employees of the shop, sorted by name.
     *
     * @param search optional; keeps employees whose name, email, phone or user id contains it (case-insensitive)
     */
    List<EmployeeResponse> listShopEmployees(String shopId, String search);

    /** Staff accounts that currently work in no shop (can be assigned), sorted by name. */
    List<EmployeeResponse> listUnassignedStaff();
}
