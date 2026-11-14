package com.saritha.fashions.modules.invoice.exception;

import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.invoice.InvoiceController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Error handling for InvoiceController only. Validation errors are returned
 * in the same { "field": "message" } format as the global handler uses for @Valid
 * errors; everything else falls through to MyGlobalExceptionHandler.
 */
@RestControllerAdvice(assignableTypes = InvoiceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InvoiceExceptionHandler {

    @ExceptionHandler(InvoiceRequestValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(InvoiceRequestValidationException exception) {
        return new ResponseEntity<>(exception.getErrors(), HttpStatus.BAD_REQUEST);
    }

    /** 409 in the same { message, status } shape as APIException errors. */
    @ExceptionHandler(InvoiceConflictException.class)
    public ResponseEntity<APIResponse> handleConflict(InvoiceConflictException exception) {
        return new ResponseEntity<>(new APIResponse(exception.getMessage(), false), HttpStatus.CONFLICT);
    }
}
