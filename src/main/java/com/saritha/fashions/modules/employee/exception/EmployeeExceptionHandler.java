package com.saritha.fashions.modules.employee.exception;

import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.employee.EmployeeController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error handling for EmployeeController only, in the usual APIResponse shape.
 * Everything else falls through to MyGlobalExceptionHandler.
 */
@RestControllerAdvice(assignableTypes = EmployeeController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EmployeeExceptionHandler {

    @ExceptionHandler(EmployeeEmailTakenException.class)
    public ResponseEntity<APIResponse> handleEmailTaken(EmployeeEmailTakenException exception) {
        return new ResponseEntity<>(new APIResponse(exception.getMessage(), false), HttpStatus.CONFLICT);
    }
}
