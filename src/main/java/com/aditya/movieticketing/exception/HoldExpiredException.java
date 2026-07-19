package com.aditya.movieticketing.exception;

import org.springframework.http.HttpStatus;

public class HoldExpiredException extends ApiException {

    public HoldExpiredException(String detail) {
        super(HttpStatus.CONFLICT, "Hold expired or invalid", detail);
    }
}
