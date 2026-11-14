package com.saritha.fashions.modules.production.exception;

import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.production.ProductionController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error handling for ProductionController only. Concurrent-change conflicts are
 * returned as 409 in the usual APIResponse shape; everything else falls through to
 * MyGlobalExceptionHandler.
 */
@RestControllerAdvice(assignableTypes = ProductionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionExceptionHandler {

    @ExceptionHandler(ItemConflictException.class)
    public ResponseEntity<APIResponse> handleConflict(ItemConflictException exception) {
        return new ResponseEntity<>(new APIResponse(exception.getMessage(), false), HttpStatus.CONFLICT);
    }
}
