package com.saritha.fashions.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ExpectationFailedException extends RuntimeException{
    private static final long serialVersionUID = 1L;
    private HttpStatus status;

    public ExpectationFailedException() {
    }

    public ExpectationFailedException(String message) {
        super(message);
    }

    public ExpectationFailedException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

}
