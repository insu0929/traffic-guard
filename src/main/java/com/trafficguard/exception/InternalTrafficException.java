package com.trafficguard.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Internal traffic error")
public class InternalTrafficException extends RuntimeException {
    public InternalTrafficException(String message) {
        super(message);
    }
}



