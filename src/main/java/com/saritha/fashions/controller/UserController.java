package com.saritha.fashions.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/api/user")
public class UserController {

    @GetMapping("/username")
    public ResponseEntity<String> currentUserName(Authentication authentication) {
        if (authentication != null) {
            return new ResponseEntity<>(authentication.getName(), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }
    }
}
