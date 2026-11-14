package com.saritha.fashions.modules.employee.exception;

/**
 * Another account already uses this email. Returned as 409 Conflict.
 */
public class EmployeeEmailTakenException extends RuntimeException {

    public EmployeeEmailTakenException(String email) {
        super("An account with email " + email + " already exists");
    }
}
