package com.saritha.fashions.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class APIException extends RuntimeException{
    private static final long serialVersionUID = 1L;
    private HttpStatus status;

    public APIException() {
    }

    public APIException(String message) {
        super(message);
    }

    public APIException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

}
