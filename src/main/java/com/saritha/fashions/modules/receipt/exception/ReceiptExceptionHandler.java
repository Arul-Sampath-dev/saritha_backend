package com.saritha.fashions.modules.receipt.exception;

import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.receipt.ReceiptController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error handling for ReceiptController only. Conflicts are 409 in the usual
 * APIResponse shape; everything else falls through to MyGlobalExceptionHandler.
 */
@RestControllerAdvice(assignableTypes = ReceiptController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReceiptExceptionHandler {

    @ExceptionHandler(ReceiptConflictException.class)
    public ResponseEntity<APIResponse> handleConflict(ReceiptConflictException exception) {
        return new ResponseEntity<>(new APIResponse(exception.getMessage(), false), HttpStatus.CONFLICT);
    }
}
