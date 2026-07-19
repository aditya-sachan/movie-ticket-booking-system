package com.aditya.movieticketing.exception;

import org.springframework.http.HttpStatus;

public class InvalidBookingStateException extends ApiException {

    public InvalidBookingStateException(String detail) {
        super(HttpStatus.CONFLICT, "Invalid booking state", detail);
    }
}
