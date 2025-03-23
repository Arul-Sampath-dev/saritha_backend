package com.saritha.fashions.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ExpectationFailedExceptionResponse {
    private String message;
    private HttpStatus status;
}
