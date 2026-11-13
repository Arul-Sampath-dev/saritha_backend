package com.saritha.fashions.modules.receiptmanagement.exception;

import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.receiptmanagement.ReceiptManagementController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error handling for ReceiptManagementController only. Conflicts are 409 in the usual
 * APIResponse shape; everything else falls through to MyGlobalExceptionHandler.
 */
@RestControllerAdvice(assignableTypes = ReceiptManagementController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReceiptManagementExceptionHandler {

    @ExceptionHandler(ReceiptConflictException.class)
    public ResponseEntity<APIResponse> handleConflict(ReceiptConflictException exception) {
        return new ResponseEntity<>(new APIResponse(exception.getMessage(), false), HttpStatus.CONFLICT);
    }
}
