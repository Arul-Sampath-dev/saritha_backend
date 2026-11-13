package com.saritha.fashions.modules.employeemanagement.exception;

import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.employeemanagement.EmployeeManagementController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error handling for EmployeeManagementController only, in the usual APIResponse shape.
 * Everything else falls through to MyGlobalExceptionHandler.
 */
@RestControllerAdvice(assignableTypes = EmployeeManagementController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EmployeeManagementExceptionHandler {

    @ExceptionHandler(EmployeeEmailTakenException.class)
    public ResponseEntity<APIResponse> handleEmailTaken(EmployeeEmailTakenException exception) {
        return new ResponseEntity<>(new APIResponse(exception.getMessage(), false), HttpStatus.CONFLICT);
    }
}
