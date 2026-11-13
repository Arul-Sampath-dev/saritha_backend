package com.saritha.fashions.modules.authentication.exception;

import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.authentication.AuthenticationController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error handling for AuthenticationController only, in the usual APIResponse shape.
 * Everything else (validation 400, APIException 400, AccessDenied 403) falls through to
 * MyGlobalExceptionHandler.
 */
@RestControllerAdvice(assignableTypes = AuthenticationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthenticationExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<APIResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return new ResponseEntity<>(new APIResponse(exception.getMessage(), false), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<APIResponse> handleEmailTaken(EmailAlreadyRegisteredException exception) {
        return new ResponseEntity<>(new APIResponse(exception.getMessage(), false), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<APIResponse> handleTooManyAttempts(TooManyAttemptsException exception) {
        return new ResponseEntity<>(new APIResponse(exception.getMessage(), false), HttpStatus.TOO_MANY_REQUESTS);
    }
}
