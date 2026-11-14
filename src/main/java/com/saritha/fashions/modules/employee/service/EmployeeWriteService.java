package com.saritha.fashions.modules.employee.service;

import com.saritha.fashions.modules.employee.payload.EmployeeResponse;
import com.saritha.fashions.modules.employee.payload.UpdateEmployeeRequest;

/**
 * Write side of employee management.
 */
public interface EmployeeWriteService {

    /** Puts the user into the shop (a user works in exactly one shop). */
    EmployeeResponse assign(String userId, String shopId);

    /** Takes the user out of the shop; the account stays and can be assigned again. */
    EmployeeResponse removeAssignment(String shopId, String userId);

    /** Changes name / email / phone / salary / address. */
    EmployeeResponse update(String shopId, String userId, UpdateEmployeeRequest request);

    /** Deletes the employee's account. */
    void delete(String shopId, String userId);
}
