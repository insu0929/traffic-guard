package com.trafficguard.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNAUTHORIZED, reason = "User identification required")
public class UserIdentificationException extends RuntimeException {
    public UserIdentificationException(String message) {
        super(message);
    }
}



